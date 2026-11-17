package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.LifecycleController
import com.github.rocketraman.bootable.boot.ServiceContext
import com.github.rocketraman.bootable.boot.StartupTimeline
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import java.time.Instant
import kotlin.time.Clock
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource
import kotlin.time.toJavaInstant

/**
 * A [LifecycleController] that traces the application's start as one trace: a root span, `start application`, with a
 * child span for each service's start, named `start` and the service's name, such as `start FlywayMigrationService`.
 * What a service does as it starts is traced within its span, such as the queries of a database migration, traced by
 * `boot-db-jdbc-otel`. A start that throws has the exception recorded on its span, with an error status, and so does
 * the root span, when that stops the application's start.
 *
 * The root span has a child span for each stage of the boot before the services start, from the [StartupTimeline]:
 * `initialize JVM classes for logging and configuration`, `plan start`, and `initialize`, so the time before the first
 * service starts is accounted for too. The SDK doesn't exist yet in those stages, so their spans are recorded
 * afterwards, with the times the timeline marked.
 *
 * The root span begins when the application began booting: with Bootable's `boot`, before logging is initialized and
 * the application built, so it covers building the application, such as decoding its config and creating its
 * services, as well as starting them, the telemetry phase's included. It ends once every service has started, when
 * the application is running. It doesn't cover the JVM's own startup, before `main`.
 *
 * The root span's start time is [clock]'s time, less how long the application has been booting, as measured by the
 * JVM's monotonic clock. Give it the clock [OpenTelemetryService] has, so its time is consistent with the SDK's time
 * for the other spans; both default to [Clock.System].
 *
 * Add it to the application's lifecycle controllers:
 *
 * ```
 * Bootable(services, defaultLifecycleControllers() + ServiceStartTracing(telemetry.openTelemetry))
 * ```
 *
 * A span is current only on the thread that calls the service's `start`, as OpenTelemetry's context is a thread-local.
 * A `start` that runs coroutines carries the span into them, onto whichever threads they run on, as a coroutine context
 * element:
 *
 * ```
 * override fun start() = runBlocking(Context.current().asContextElement()) { … }
 * ```
 *
 * Without it, work that a `start` runs on another dispatcher, such as an Exposed `withTransaction`, isn't within the
 * span, and its queries each start a trace of their own.
 *
 * A service started later, outside the application's start, such as by a controller that starts services on command,
 * gets a span of its own, a child of whatever span is current then.
 */
class ServiceStartTracing(
  openTelemetry: OpenTelemetry,
  private val clock: Clock = Clock.System,
) : LifecycleController {
  private val tracer: Tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE)

  override fun aroundAppStart(serviceContext: ServiceContext, timeline: StartupTimeline, start: () -> Unit) {
    // the wall-clock time of each mark, relative to one reading of the clock, so the spans are consistent
    val reference = TimeSource.Monotonic.markNow()
    val now = clock.now()
    fun at(mark: ComparableTimeMark): Instant = (now - (reference - mark)).toJavaInstant()

    tracer.spanBuilder(APP_START_SPAN).setNoParent().setStartTimestamp(at(timeline.start))
      .runInSpan {
        timeline.stages.forEach { stage ->
          tracer.spanBuilder(stage.name).setStartTimestamp(at(stage.start)).startSpan().end(at(stage.end))
        }
        start()
      }
  }

  override fun aroundServiceStart(appService: AppService, start: () -> Unit) {
    tracer.spanBuilder("start ${appService.name()}").runInSpan(start)
  }

  private fun SpanBuilder.runInSpan(block: () -> Unit) {
    val span: Span = startSpan()
    try {
      span.makeCurrent().use { block() }
    } catch (e: Throwable) {
      span.recordException(e)
      span.setStatus(StatusCode.ERROR)
      throw e
    } finally {
      span.end()
    }
  }

  companion object {
    /** The instrumentation scope of the spans. */
    const val INSTRUMENTATION_SCOPE = "com.github.rocketraman.bootable.boot"

    /** The name of the root span, of the application's whole start. */
    const val APP_START_SPAN = "start application"
  }
}
