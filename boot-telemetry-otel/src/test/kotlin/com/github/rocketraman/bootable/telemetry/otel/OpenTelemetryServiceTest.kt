package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.startPlan
import com.github.rocketraman.bootable.config.Masked
import de.infix.testBalloon.framework.core.testSuite
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.api.common.Attributes
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours

private fun Resource.attribute(key: String) = getAttribute(AttributeKey.stringKey(key))

val OpenTelemetryServiceTest by testSuite {
  testSuite("the resource") {
    test("names the service, its version, and its environment") {
      val resource = resource(
        TelemetryConfig(serviceName = "orders", serviceVersion = "1.0", environment = "prod", resourceAttributes = mapOf("team" to "payments")),
      )
      assert(resource.attribute("service.name") == "orders")
      assert(resource.attribute("service.version") == "1.0")
      assert(resource.attribute("deployment.environment.name") == "prod")
      assert(resource.attribute("team") == "payments")
      // the SDK's own attributes are kept
      assert(resource.attribute("telemetry.sdk.language") == "java")
    }

    test("gives every instance its own id, unless one is configured") {
      val first = resource(TelemetryConfig()).attribute("service.instance.id")
      val second = resource(TelemetryConfig()).attribute("service.instance.id")
      assert(first != null && second != null && first != second)
      assert(resource(TelemetryConfig(serviceInstanceId = "orders-0")).attribute("service.instance.id") == "orders-0")
      val fromAttributes = resource(TelemetryConfig(resourceAttributes = mapOf("service.instance.id" to "pod-1")))
      assert(fromAttributes.attribute("service.instance.id") == "pod-1")
    }

    test("falls back to OTEL_SERVICE_NAME and OTEL_RESOURCE_ATTRIBUTES") {
      val env = mapOf(
        "OTEL_SERVICE_NAME" to "from-env",
        "OTEL_RESOURCE_ATTRIBUTES" to "k8s.pod.name=orders-1,team=a%20team",
      )
      val resource = resource(TelemetryConfig().withOtelEnvironment(env))
      assert(resource.attribute("service.name") == "from-env")
      assert(resource.attribute("k8s.pod.name") == "orders-1")
      assert(resource.attribute("team") == "a team")
    }

    test("prefers the config to the environment variables, and the typed settings to resourceAttributes") {
      val env = mapOf("OTEL_SERVICE_NAME" to "from-env", "OTEL_RESOURCE_ATTRIBUTES" to "team=env-team")
      val config = TelemetryConfig(
        serviceName = "typed",
        resourceAttributes = mapOf("service.name" to "from-map", "team" to "config-team"),
      )
      val resource = resource(config.withOtelEnvironment(env))
      assert(resource.attribute("service.name") == "typed")
      assert(resource.attribute("team") == "config-team")
    }

    test("prefers OTEL_SERVICE_NAME to a service.name in resourceAttributes, as the typed setting it fills") {
      val config = TelemetryConfig(resourceAttributes = mapOf("service.name" to "from-map"))
      val resource = resource(config.withOtelEnvironment(mapOf("OTEL_SERVICE_NAME" to "from-env")))
      assert(resource.attribute("service.name") == "from-env")
    }

    test("merges OTEL_EXPORTER_OTLP_HEADERS with the config's headers, whose values win") {
      val config = TelemetryConfig(otlp = OtlpConfig(headers = mapOf("api-key" to Masked("configured"))))
      val env = mapOf("OTEL_EXPORTER_OTLP_HEADERS" to "api-key=from-env,tenant=a%20b")
      assert(config.withOtelEnvironment(env).otlp.headers == mapOf("api-key" to Masked("configured"), "tenant" to Masked("a b")))
    }

    test("treats an empty or blank standard variable as unset, as the specification says") {
      val env = mapOf(
        "OTEL_SERVICE_NAME" to "",
        "OTEL_RESOURCE_ATTRIBUTES" to " ",
        "OTEL_EXPORTER_OTLP_ENDPOINT" to "",
        "OTEL_EXPORTER_OTLP_HEADERS" to "  ",
      )
      assert(TelemetryConfig().withOtelEnvironment(env) == TelemetryConfig())
      // and the SDK builds, rather than failing on an endpoint of "/v1/traces"
      OpenTelemetryService(testConfig().withOtelEnvironment(env)).shutdown()
    }

    test("is passed through the customizers") {
      val customizer = object : OpenTelemetryCustomizer {
        override fun customizeResource(resource: Resource) =
          resource.merge(Resource.create(Attributes.of(AttributeKey.stringKey("cloud.platform"), "gcp_cloud_run")))
      }
      val service = OpenTelemetryService(testConfig(), listOf(customizer))
      assert(service.resource.attribute("cloud.platform") == "gcp_cloud_run")
      assert(service.resource.attribute("service.name") == "test-service")
    }
  }

  testSuite("traces") {
    test("are recorded with the resource") {
      val telemetry = InMemoryTelemetry()
      val service = OpenTelemetryService(testConfig(), listOf(telemetry))
      service.openTelemetry.getTracer("test").spanBuilder("work").startSpan().end()
      val span = telemetry.spans.finishedSpanItems.single()
      assert(span.name == "work")
      assert(span.resource.attribute("service.name") == "test-service")
    }

    test("are sampled by sampleRatio") {
      val telemetry = InMemoryTelemetry()
      val service = OpenTelemetryService(testConfig(traces = TracesConfig(sampleRatio = 0.0)), listOf(telemetry))
      service.openTelemetry.getTracer("test").spanBuilder("work").startSpan().end()
      assert(telemetry.spans.finishedSpanItems.isEmpty())
    }

    test("are not recorded when off, without asking the customizers for a tracer provider") {
      val telemetry = InMemoryTelemetry()
      val service = OpenTelemetryService(testConfig(traces = TracesConfig(enabled = false)), listOf(telemetry))
      val span = service.openTelemetry.getTracer("test").spanBuilder("work").startSpan()
      span.end()
      assert(!span.isRecording)
      assert(telemetry.spans.finishedSpanItems.isEmpty())
    }
  }

  testSuite("metrics") {
    test("are recorded with the resource") {
      val telemetry = InMemoryTelemetry()
      val service = OpenTelemetryService(testConfig(), listOf(telemetry))
      service.openTelemetry.getMeter("test").counterBuilder("orders.placed").build().add(2)
      val metric = telemetry.metrics.collectAllMetrics().single { it.name == "orders.placed" }
      assert(metric.longSumData.points.single().value == 2L)
      assert(metric.resource.attribute("service.name") == "test-service")
    }

    test("include the JVM's runtime metrics once started, when runtime is on") {
      val telemetry = InMemoryTelemetry()
      val service = OpenTelemetryService(testConfig(metrics = MetricsConfig(runtime = true)), listOf(telemetry))
      assert(telemetry.metricNames().none { it.startsWith("jvm.") })
      service.start()
      try {
        assert("jvm.memory.used" in telemetry.metricNames())
        assert("jvm.thread.count" in telemetry.metricNames())
      } finally {
        service.shutdown()
      }
    }

    test("leave out the JVM's runtime metrics when runtime is off") {
      val telemetry = InMemoryTelemetry()
      val service = OpenTelemetryService(testConfig(metrics = MetricsConfig(runtime = false)), listOf(telemetry))
      service.start()
      assert(telemetry.metricNames().none { it.startsWith("jvm.") })
      service.shutdown()
    }

    test("are a no-op when off") {
      val telemetry = InMemoryTelemetry()
      val service = OpenTelemetryService(testConfig(metrics = MetricsConfig(enabled = false)), listOf(telemetry))
      service.openTelemetry.getMeter("test").counterBuilder("orders.placed").build().add(2)
      assert(telemetry.metrics.collectAllMetrics().isEmpty())
    }
  }

  testSuite("OTLP export") {
    test("shutdown exports the last spans and metrics, with the resource and headers") {
      withCollector { endpoint, requests ->
        val config = testConfig(
          otlp = OtlpConfig(endpoint = endpoint, headers = mapOf("api-key" to Masked("secret-key"))),
          // far longer than the test, so only the shutdown exports metrics
          metrics = MetricsConfig(interval = 1.hours, runtime = false),
        )
        val service = OpenTelemetryService(config)
        service.start()
        service.openTelemetry.getTracer("test").spanBuilder("checkout").setSpanKind(SpanKind.SERVER).startSpan().end()
        service.openTelemetry.getMeter("test").counterBuilder("orders.placed").build().add(3)

        service.shutdown()

        val traces = requests.single { it.path == "/v1/traces" }
        assert(traces.headers["Api-key"] == "secret-key")
        assert("checkout" in traces.body && "test-service" in traces.body)
        val metrics = requests.single { it.path == "/v1/metrics" }
        assert("orders.placed" in metrics.body && "test-service" in metrics.body)
      }
    }

    test("goes to OTEL_EXPORTER_OTLP_ENDPOINT, with OTEL_EXPORTER_OTLP_HEADERS, when the config sets none") {
      withCollector { endpoint, requests ->
        val env = mapOf("OTEL_EXPORTER_OTLP_ENDPOINT" to "$endpoint/", "OTEL_EXPORTER_OTLP_HEADERS" to "api-key=from-env")
        val service = OpenTelemetryService(testConfig().withOtelEnvironment(env))
        service.openTelemetry.getTracer("test").spanBuilder("checkout").startSpan().end()
        service.shutdown()
        assert(requests.single { it.path == "/v1/traces" }.headers["Api-key"] == "from-env")
      }
    }

    test("can be turned off per signal") {
      withCollector { endpoint, requests ->
        val config = testConfig(
          otlp = OtlpConfig(endpoint = endpoint),
          traces = TracesConfig(otlp = false),
          metrics = MetricsConfig(runtime = false),
        )
        val service = OpenTelemetryService(config)
        service.openTelemetry.getTracer("test").spanBuilder("checkout").startSpan().end()
        service.openTelemetry.getMeter("test").counterBuilder("orders.placed").build().add(3)
        service.shutdown()
        assert(requests.map { it.path } == listOf("/v1/metrics"))
      }
    }

    test("the exporters go through the customizers, with the endpoint they send to") {
      withCollector { endpoint, requests ->
        val endpoints = mutableListOf<String>()
        val customizer = object : OpenTelemetryCustomizer {
          override fun customizeSpanExporter(builder: io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder, endpoint: String) {
            endpoints += endpoint
            builder.addHeader("authorization", "Bearer token")
          }
        }
        val service = OpenTelemetryService(testConfig(otlp = OtlpConfig(endpoint = endpoint)), listOf(customizer))
        service.openTelemetry.getTracer("test").spanBuilder("checkout").startSpan().end()
        service.shutdown()
        assert(endpoints == listOf("$endpoint/v1/traces"))
        assert(requests.single { it.path == "/v1/traces" }.headers["Authorization"] == "Bearer token")
      }
    }

    test("sends every customizer's export headers, fetched for each request") {
      withCollector { endpoint, requests ->
        var token = 0
        fun headers(vararg headers: Pair<String, String>) = object : OpenTelemetryCustomizer {
          override fun exportHeaders(endpoint: String): () -> Map<String, String> = { mapOf(*headers) }
        }
        val refreshing = object : OpenTelemetryCustomizer {
          override fun exportHeaders(endpoint: String): () -> Map<String, String> = { mapOf("token" to "${++token}") }
        }
        val customizers = listOf(headers("a" to "1", "b" to "1"), refreshing, headers("b" to "2"))
        val config = testConfig(otlp = OtlpConfig(endpoint = endpoint, headers = mapOf("fixed" to Masked("f"))))
        val service = OpenTelemetryService(config, customizers)
        val tracer = service.openTelemetry.getTracer("test")
        tracer.spanBuilder("first").startSpan().end()
        // two export requests, each with its own token
        (service.openTelemetry as OpenTelemetrySdk).sdkTracerProvider.forceFlush().join(10, TimeUnit.SECONDS)
        tracer.spanBuilder("second").startSpan().end()
        service.shutdown()

        val traces = requests.filter { it.path == "/v1/traces" }
        assert(traces.size == 2)
        traces.forEach {
          assert(it.headers["A"] == "1" && it.headers["B"] == "2" && it.headers["Fixed"] == "f")
        }
        assert(traces.map { it.headers["Token"] }.toSet().size == 2)
      }
    }

    test("keeps the headers out of toString") {
      assert("secret-key" !in OtlpConfig(headers = mapOf("api-key" to Masked("secret-key"))).toString())
    }
  }

  test("starts before, and stops after, the services that record telemetry") {
    val telemetry = OpenTelemetryService(testConfig())
    // e.g. a database pool, which declares nothing about telemetry
    val pool = object : AppService {
      override fun ordering() = Ordering(DefaultPhase.Infrastructure)
    }
    val application = object : AppService {}
    repeat(10) {
      val plan = startPlan(listOf(application, pool, telemetry), tiebreaker = StartOrder.Tiebreaker.Shuffled())
      assert(plan == listOf(telemetry, pool, application))
    }
  }

  test("parses the key=value lists of the OpenTelemetry environment variables") {
    assert(parseKeyValues("a=1, b = x%3Dy ,bad,=no") == mapOf("a" to "1", "b" to "x=y"))
    assert(parseKeyValues(null).isEmpty())
  }
}
