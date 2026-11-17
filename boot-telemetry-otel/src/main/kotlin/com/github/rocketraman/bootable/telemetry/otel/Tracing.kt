package com.github.rocketraman.bootable.telemetry.otel

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.extension.kotlin.getOpenTelemetryContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

// by name, rather than logger(), whose name resolution would need native-image metadata for this file's class
private val log = logger("com.github.rocketraman.bootable.telemetry.otel.Tracing")

// the names of the spans withSpan has warned about, so it warns about each only once
private val spansWithoutParent: MutableSet<String> = ConcurrentHashMap.newKeySet()

/**
 * Runs [block] in a new span named [name], a child of the current span, such as the request span of a traced server.
 * The span is current for [block], across every thread it suspends and resumes on, so spans and log events within it,
 * even on another dispatcher, belong to its trace. It ends when [block] does, with an error status and the exception
 * recorded if [block] throws, or, if the coroutine is cancelled, an error status of `cancelled`.
 *
 * ```
 * suspend fun find(id: String) = tracer.withSpan("orders.find") { span ->
 *   span.setAttribute("order.id", id)
 *   repository.find(id)
 * }
 * ```
 *
 * A span from `withSpan` is expected to have a parent. Without a current span, it starts a trace of its own, and logs a
 * warning, once for each span name: usually the caller's trace context didn't reach it, such as across a new
 * `CoroutineScope` or a callback from a Java library, so its trace is cut off from the operation it belongs to. To
 * start a trace on purpose, such as for a scheduled job, use [withRootSpan] instead.
 */
suspend fun <T> Tracer.withSpan(
  name: String,
  kind: SpanKind = SpanKind.INTERNAL,
  block: suspend (Span) -> T,
): T {
  val parent = currentContext()
  val span = spanBuilder(name).setParent(parent).setSpanKind(kind).startSpan()
  // an invalid span is what a no-op tracer starts, when tracing is off, and so it has no trace to cut off
  if (span.spanContext.isValid && !Span.fromContext(parent).spanContext.isValid && spansWithoutParent.add(name)) {
    log.warn {
      "Span '$name' has no parent span, so it starts a trace of its own. If it should, start it with withRootSpan; " +
        "otherwise, check that its caller's trace context reaches it."
    }
  }
  return runInSpan(parent, span, block)
}

/**
 * Runs [block] in a new span named [name] that starts a trace, even within another span, and is otherwise as
 * [withSpan]: for an operation that is the start of a trace, such as a scheduled job, or a message consumed from a
 * queue.
 *
 * A trace started for work that another operation kicked off but doesn't wait for, such as a background task started
 * by a request, can say so with [links] to that operation's span, rather than being its child.
 *
 * [sample] is whether the trace is sampled as the SDK's sampler decides, such as at the configured `sampleRatio`, or
 * always, with [Sample.Always], for a trace too rare or too valuable to lose, such as a nightly job's.
 *
 * ```
 * suspend fun purge() = tracer.withRootSpan("orders.purge") {
 *   repository.purgeExpired()
 * }
 * ```
 */
suspend fun <T> Tracer.withRootSpan(
  name: String,
  kind: SpanKind = SpanKind.INTERNAL,
  links: Collection<SpanContext> = emptyList(),
  sample: Sample = Sample.BySampler,
  block: suspend (Span) -> T,
): T {
  val span = spanBuilder(name).setNoParent().setSpanKind(kind)
    .apply { links.forEach { addLink(it) } }
    .apply { if (sample == Sample.Always) setAttribute(ALWAYS_SAMPLE, true) }
    .startSpan()
  return runInSpan(currentContext(), span, block)
}

/** How a trace that [withRootSpan] starts is sampled. */
enum class Sample {
  /** As the SDK's sampler decides, such as at the configured `sampleRatio`. */
  BySampler,

  /**
   * Always, whatever the SDK's sampler would decide. The root span carries [ALWAYS_SAMPLE], which the sampler of
   * [OpenTelemetryService]'s SDK honors; the spans within the trace follow the root, as long as the sampler samples by
   * the parent, as the default does. Under the OpenTelemetry Java agent, whose SDK is its own, it has no effect.
   */
  Always,
}

/**
 * Marks a root span whose trace [OpenTelemetryService]'s sampler always samples, as [withRootSpan] does with
 * [Sample.Always], and [ServiceStartTracing] does for the application's start. Set it on a span builder, before the
 * span starts: the sampler sees only the attributes the span starts with.
 */
val ALWAYS_SAMPLE: AttributeKey<Boolean> = AttributeKey.booleanKey("bootable.sample.always")

/** The coroutine's context, which a traced server's request span is in, falling back to the thread's. */
private suspend fun currentContext(): Context =
  currentCoroutineContext().getOpenTelemetryContext().takeIf { it != Context.root() } ?: Context.current()

private suspend fun <T> runInSpan(parent: Context, span: Span, block: suspend (Span) -> T): T {
  return try {
    withContext(parent.with(span).asContextElement()) { block(span) }
  } catch (e: Throwable) {
    if (e is CancellationException && !currentCoroutineContext().isActive) {
      // this coroutine was cancelled: not the block's error, so no exception to record
      span.setStatus(StatusCode.ERROR, "cancelled")
    } else {
      // includes a CancellationException while this coroutine is still active, such as a timeout within the block,
      // or awaiting another coroutine that was cancelled, which is the block's error
      span.recordException(e)
      span.setStatus(StatusCode.ERROR)
    }
    throw e
  } finally {
    span.end()
  }
}
