package com.github.rocketraman.bootable.telemetry.otel

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.extension.kotlin.getOpenTelemetryContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] in a new span named [name], a child of the current span, if there is one, such as the request span of a
 * traced server. The span is current for [block], across every thread it suspends and resumes on, so spans and log
 * events within it, even on another dispatcher, belong to its trace. It ends when [block] does, with an error status
 * and the exception recorded if [block] throws, or, if the coroutine is cancelled, an error status of `cancelled`.
 *
 * ```
 * suspend fun find(id: String) = tracer.withSpan("orders.find") { span ->
 *   span.setAttribute("order.id", id)
 *   repository.find(id)
 * }
 * ```
 */
suspend fun <T> Tracer.withSpan(
  name: String,
  kind: SpanKind = SpanKind.INTERNAL,
  block: suspend (Span) -> T,
): T {
  // the coroutine's context, which a traced server's request span is in, falling back to the thread's
  val parent = currentCoroutineContext().getOpenTelemetryContext().takeIf { it != Context.root() } ?: Context.current()
  val span = spanBuilder(name).setParent(parent).setSpanKind(kind).startSpan()
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
