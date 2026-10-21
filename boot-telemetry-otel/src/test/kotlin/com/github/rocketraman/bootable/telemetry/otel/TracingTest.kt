package com.github.rocketraman.bootable.telemetry.otel

import de.infix.testBalloon.framework.core.testSuite
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.logging.log4j.core.impl.ContextDataInjectorFactory
import org.apache.logging.log4j.util.SortedArrayStringMap
import kotlin.time.Duration.Companion.milliseconds

private fun tracing(sampleRatio: Double = 1.0, vararg customizers: OpenTelemetryCustomizer): Pair<OpenTelemetryService, InMemoryTelemetry> {
  val telemetry = InMemoryTelemetry()
  val service = OpenTelemetryService(testConfig(traces = TracesConfig(sampleRatio = sampleRatio)), listOf(telemetry, *customizers))
  return service to telemetry
}

val TracingTest by testSuite {
  testSuite("withSpan") {
    test("runs the block in a span that is a child of the current one") {
      val (service, telemetry) = tracing()
      val tracer = service.openTelemetry.getTracer("test")
      val parent = tracer.spanBuilder("request").setSpanKind(SpanKind.SERVER).startSpan()
      val result = withContext(parent.asContextElement()) {
        tracer.withSpan("orders.find") { span ->
          span.setAttribute("order.id", "42")
          "found"
        }
      }
      parent.end()

      assert(result == "found")
      val child = telemetry.spans.finishedSpanItems.single { it.name == "orders.find" }
      assert(child.parentSpanId == parent.spanContext.spanId)
      assert(child.traceId == parent.spanContext.traceId)
      assert(child.attributes.get(AttributeKey.stringKey("order.id")) == "42")
    }

    test("keeps its span current across dispatchers, for nested spans") {
      val (service, telemetry) = tracing()
      val tracer = service.openTelemetry.getTracer("test")
      tracer.withSpan("outer") {
        withContext(Dispatchers.IO) {
          assert(Span.current().spanContext.spanId == it.spanContext.spanId)
          tracer.withSpan("inner") { }
        }
      }
      val spans = telemetry.spans.finishedSpanItems.associateBy { it.name }
      assert(spans.getValue("inner").parentSpanId == spans.getValue("outer").spanId)
    }

    test("starts a trace when there is no current span") {
      val (service, telemetry) = tracing()
      service.openTelemetry.getTracer("test").withSpan("job") { }
      assert(!telemetry.spans.finishedSpanItems.single().parentSpanContext.isValid)
    }

    test("records the exception, with an error status, when the block throws") {
      val (service, telemetry) = tracing()
      val failure = runCatching {
        service.openTelemetry.getTracer("test").withSpan<Unit>("orders.find") { error("database down") }
      }.exceptionOrNull()
      assert(failure?.message == "database down")
      val span = telemetry.spans.finishedSpanItems.single()
      assert(span.status.statusCode == StatusCode.ERROR)
      assert(span.events.single().name == "exception")
    }

    test("marks the span cancelled, recording no exception, when its coroutine is cancelled") {
      val (service, telemetry) = tracing()
      coroutineScope {
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
          service.openTelemetry.getTracer("test").withSpan("job") { awaitCancellation() }
        }
        job.cancelAndJoin()
      }
      val span = telemetry.spans.finishedSpanItems.single()
      assert(span.status.statusCode == StatusCode.ERROR)
      assert(span.status.description == "cancelled")
      assert(span.events.isEmpty())
    }

    test("records a timeout within the block as its error, since the coroutine itself isn't cancelled") {
      val (service, telemetry) = tracing()
      val failure = runCatching {
        service.openTelemetry.getTracer("test").withSpan("orders.find") { withTimeout(10.milliseconds) { awaitCancellation() } }
      }.exceptionOrNull()
      assert(failure is TimeoutCancellationException)
      val span = telemetry.spans.finishedSpanItems.single()
      assert(span.status.statusCode == StatusCode.ERROR)
      assert(span.events.single().name == "exception")
    }

    test("puts the trace and span ids in log events' context data, for the log layouts") {
      val (service, _) = tracing()
      service.openTelemetry.getTracer("test").withSpan("orders.find") { span ->
        // as log4j does for every log event
        val contextData = ContextDataInjectorFactory.createInjector().injectContextData(null, SortedArrayStringMap())
        assert(contextData.getValue<String>("trace_id") == span.spanContext.traceId)
        assert(contextData.getValue<String>("span_id") == span.spanContext.spanId)
      }
    }
  }

  testSuite("excludeUrlPathsFromTraces") {
    val urlPath = AttributeKey.stringKey("url.path")
    fun startServerSpan(service: OpenTelemetryService, path: String, parent: Span? = null) =
      service.openTelemetry.getTracer("test").spanBuilder("GET").setSpanKind(SpanKind.SERVER)
        .apply { if (parent != null) setParent(parent.storeInContext(io.opentelemetry.context.Context.root())) }
        .setAttribute(urlPath, path).startSpan()

    test("records no trace for a server request to an excluded path") {
      val (service, telemetry) = tracing(1.0, excludeUrlPathsFromTraces(setOf("/metrics")))
      startServerSpan(service, "/metrics").end()
      startServerSpan(service, "/orders").end()
      assert(telemetry.spans.finishedSpanItems.map { it.attributes.get(urlPath) } == listOf("/orders"))
    }

    test("leaves other spans to the sampler it wraps") {
      val (service, telemetry) = tracing(0.0, excludeUrlPathsFromTraces(setOf("/metrics")))
      startServerSpan(service, "/orders").end()
      assert(telemetry.spans.finishedSpanItems.isEmpty())
    }

    test("keeps a request that continues a caller's trace") {
      val (service, telemetry) = tracing(1.0, excludeUrlPathsFromTraces(setOf("/metrics")))
      val caller = service.openTelemetry.getTracer("test").spanBuilder("caller").startSpan()
      startServerSpan(service, "/metrics", parent = caller).end()
      caller.end()
      assert(telemetry.spans.finishedSpanItems.any { it.attributes.get(urlPath) == "/metrics" })
    }
  }
}

