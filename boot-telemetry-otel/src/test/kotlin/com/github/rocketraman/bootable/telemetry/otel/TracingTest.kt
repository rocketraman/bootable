package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.test.runSuspendCatching
import de.infix.testBalloon.framework.core.testSuite
import io.opentelemetry.api.OpenTelemetry
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
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.impl.ContextDataInjectorFactory
import org.apache.logging.log4j.util.SortedArrayStringMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

private fun tracing(sampleRatio: Double = 1.0, vararg customizers: OpenTelemetryCustomizer): Pair<OpenTelemetryService, InMemoryTelemetry> {
  val telemetry = InMemoryTelemetry()
  val service = OpenTelemetryService(testConfig(traces = TracesConfig(sampleRatio = sampleRatio)), listOf(telemetry, *customizers))
  return service to telemetry
}

/** The warnings that the tracing helpers log while [block] runs. */
private suspend fun tracingWarnings(block: suspend () -> Unit): List<String> {
  val warnings = CopyOnWriteArrayList<String>()
  val appender = object : AbstractAppender("tracing-warnings", null, null, true, Property.EMPTY_ARRAY) {
    override fun append(event: LogEvent) {
      if (event.level == Level.WARN) warnings += event.message.formattedMessage
    }
  }
  appender.start()
  val name = "com.github.rocketraman.bootable.telemetry.otel.Tracing"
  val logger = LogManager.getLogger(name) as Logger
  val level = logger.level
  // log4j's default config, without one of the application's, logs only errors
  Configurator.setLevel(name, Level.WARN)
  logger.addAppender(appender)
  try {
    block()
  } finally {
    logger.removeAppender(appender)
    Configurator.setLevel(name, level)
    appender.stop()
  }
  return warnings
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

    test("starts a trace, and warns once for each span name, when there is no current span") {
      val (service, telemetry) = tracing()
      val tracer = service.openTelemetry.getTracer("test")
      val warnings = tracingWarnings {
        tracer.withSpan("orphan.a") { }
        tracer.withSpan("orphan.a") { }
        tracer.withSpan("orphan.b") { }
      }
      assert(telemetry.spans.finishedSpanItems.none { it.parentSpanContext.isValid })
      assert(warnings.size == 2)
      assert(warnings[0].contains("'orphan.a'") && warnings[0].contains("withRootSpan"))
      assert(warnings[1].contains("'orphan.b'"))
    }

    test("doesn't warn of a missing parent when it has one") {
      val (service, _) = tracing()
      val tracer = service.openTelemetry.getTracer("test")
      val warnings = tracingWarnings {
        tracer.withRootSpan("parented.root") { tracer.withSpan("parented.child") { } }
      }
      assert(warnings.isEmpty())
    }

    test("doesn't warn of a missing parent when tracing is off") {
      val warnings = tracingWarnings { OpenTelemetry.noop().getTracer("test").withSpan("untraced") { } }
      assert(warnings.isEmpty())
    }
  }

  testSuite("withRootSpan") {
    test("starts a trace, even within another span, keeping it current for the block") {
      val (service, telemetry) = tracing()
      val tracer = service.openTelemetry.getTracer("test")
      tracer.withRootSpan("request") {
        tracer.withRootSpan("job") { job ->
          assert(Span.current().spanContext.spanId == job.spanContext.spanId)
          tracer.withSpan("job.step") { }
        }
      }
      val spans = telemetry.spans.finishedSpanItems.associateBy { it.name }
      assert(!spans.getValue("job").parentSpanContext.isValid)
      assert(spans.getValue("job").traceId != spans.getValue("request").traceId)
      assert(spans.getValue("job.step").parentSpanId == spans.getValue("job").spanId)
    }

    test("with Sample.Always, samples the trace whatever the ratio, and only then") {
      val (service, telemetry) = tracing(sampleRatio = 0.0)
      val tracer = service.openTelemetry.getTracer("test")
      tracer.withRootSpan("nightly", sample = Sample.Always) {
        tracer.withSpan("nightly.step") { }
      }
      tracer.withRootSpan("routine") {
        tracer.withSpan("routine.step") { }
      }
      assert(telemetry.spans.finishedSpanItems.map { it.name }.toSet() == setOf("nightly", "nightly.step"))
    }

    test("links the trace to the spans it is given") {
      val (service, telemetry) = tracing()
      val tracer = service.openTelemetry.getTracer("test")
      tracer.withRootSpan("request") { request ->
        tracer.withRootSpan("background", links = listOf(request.spanContext)) { }
      }
      val spans = telemetry.spans.finishedSpanItems.associateBy { it.name }
      assert(spans.getValue("background").links.single().spanContext.spanId == spans.getValue("request").spanId)
    }

    test("records the exception, with an error status, when the block throws") {
      val (service, telemetry) = tracing()
      runSuspendCatching { service.openTelemetry.getTracer("test").withRootSpan<Unit>("job") { error("failed") } }
      val span = telemetry.spans.finishedSpanItems.single()
      assert(span.status.statusCode == StatusCode.ERROR)
      assert(span.events.single().name == "exception")
    }

    test("records the exception, with an error status, when the block throws") {
      val (service, telemetry) = tracing()
      val failure = runSuspendCatching {
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
      val failure = runSuspendCatching {
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

