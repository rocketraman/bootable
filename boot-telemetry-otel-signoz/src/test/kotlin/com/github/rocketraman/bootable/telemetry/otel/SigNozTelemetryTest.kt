package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.Masked
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.sun.net.httpserver.HttpServer
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

private fun service(sigNoz: SigNozTelemetry, endpoint: String? = null, environment: String? = "prod") = OpenTelemetryService(
  TelemetryConfig(
    serviceName = "orders-api",
    environment = environment,
    otlp = OtlpConfig(endpoint = endpoint),
    metrics = MetricsConfig(runtime = false),
    registerGlobal = false,
  ),
  listOf(sigNoz),
)

/** The headers of every export to a local OTLP receiver, by path, after a span and a counter are recorded. */
private fun exportedHeaders(sigNoz: SigNozTelemetry): Map<String, List<Map<String, String>>> {
  val requests = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
  val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  server.createContext("/") { exchange ->
    requests += exchange.requestURI.path to exchange.requestHeaders.mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString() }
    exchange.sendResponseHeaders(200, -1)
    exchange.close()
  }
  server.start()
  try {
    val service = service(sigNoz, "http://127.0.0.1:${server.address.port}")
    service.openTelemetry.getTracer("test").spanBuilder("checkout").startSpan().end()
    service.openTelemetry.getMeter("test").counterBuilder("orders.placed").build().add(1)
    service.shutdown()
  } finally {
    server.stop(0)
  }
  return requests.groupBy({ it.first }, { it.second })
}

private fun temporality(config: SigNozTelemetryConfig, type: InstrumentType): AggregationTemporality {
  val builder = OtlpHttpMetricExporter.builder()
  SigNozTelemetry(config).customizeMetricExporter(builder, "http://otel-collector:4318/v1/metrics")
  return builder.build().getAggregationTemporality(type)
}

private fun resourceOf(sigNoz: SigNozTelemetry, environment: String? = "prod") = service(sigNoz, environment = environment).resource

val SigNozTelemetryTest by testSuite {
  testSuite("export") {
    test("carries the ingestion key when it goes to SigNoz Cloud, and not when it goes elsewhere") {
      val sigNoz = SigNozTelemetry(SigNozTelemetryConfig(ingestionKey = Masked("key")))
      assert(sigNoz.authenticates("https://ingest.us.signoz.cloud:443/v1/traces"))
      assert(sigNoz.authenticates("https://ingest.eu.signoz.cloud:443/v1/metrics"))
      assert(!sigNoz.authenticates("http://signoz-otel-collector:4318/v1/traces"))
      assert(!sigNoz.authenticates("https://notsignoz.cloud/v1/traces"))
      assert(SigNozTelemetry(SigNozTelemetryConfig(authenticate = true)).authenticates("http://otel-collector:4318/v1/traces"))
      assert(!SigNozTelemetry(SigNozTelemetryConfig(authenticate = false)).authenticates("https://ingest.us.signoz.cloud:443"))
    }

    test("sends the ingestion key as signoz-ingestion-key, with traces and metrics") {
      val headers = exportedHeaders(SigNozTelemetry(SigNozTelemetryConfig(ingestionKey = Masked("test-key"), authenticate = true)))
      assert(headers.getValue("/v1/traces").single()["signoz-ingestion-key"] == "test-key")
      assert(headers.getValue("/v1/metrics").single()["signoz-ingestion-key"] == "test-key")
    }

    test("sends no key to a self-hosted SigNoz") {
      val headers = exportedHeaders(SigNozTelemetry(SigNozTelemetryConfig(ingestionKey = Masked("test-key"))))
      assert(headers.values.flatten().none { "signoz-ingestion-key" in it })
    }

    test("fails at boot, rather than on every export, without an ingestion key") {
      val sigNoz = SigNozTelemetry(SigNozTelemetryConfig(authenticate = true))
      val failure = runCatching { service(sigNoz, "http://127.0.0.1:1") }.exceptionOrNull()
      assert(failure?.message?.contains("telemetry.signoz.ingestionKey") == true)
    }

    test("is cumulative by default, as in the SDK") {
      assert(temporality(SigNozTelemetryConfig(), InstrumentType.COUNTER) == AggregationTemporality.CUMULATIVE)
      assert(temporality(SigNozTelemetryConfig(), InstrumentType.HISTOGRAM) == AggregationTemporality.CUMULATIVE)
    }

    test("is delta for counters and histograms, but not up-down counters, when configured") {
      val delta = SigNozTelemetryConfig(temporality = MetricsTemporality.Delta)
      assert(temporality(delta, InstrumentType.COUNTER) == AggregationTemporality.DELTA)
      assert(temporality(delta, InstrumentType.OBSERVABLE_COUNTER) == AggregationTemporality.DELTA)
      assert(temporality(delta, InstrumentType.HISTOGRAM) == AggregationTemporality.DELTA)
      assert(temporality(delta, InstrumentType.UP_DOWN_COUNTER) == AggregationTemporality.CUMULATIVE)
    }

    test("keeps the ingestion key out of toString") {
      assert("secret-key" !in SigNozTelemetry(SigNozTelemetryConfig(ingestionKey = Masked("secret-key"))).toString())
    }
  }

  testSuite("the resource") {
    test("also has the environment as deployment.environment, for SigNoz's filters") {
      val resource = resourceOf(SigNozTelemetry())
      assert(resource.getAttribute(AttributeKey.stringKey("deployment.environment")) == "prod")
      assert(resource.getAttribute(AttributeKey.stringKey("deployment.environment.name")) == "prod")
    }

    test("keeps a deployment.environment of the application's own") {
      val config = TelemetryConfig(environment = "prod", resourceAttributes = mapOf("deployment.environment" to "own"))
      val resource = OpenTelemetryService(config.copy(registerGlobal = false), listOf(SigNozTelemetry())).resource
      assert(resource.getAttribute(AttributeKey.stringKey("deployment.environment")) == "own")
    }

    test("is left alone without an environment, or when turned off") {
      assert(resourceOf(SigNozTelemetry(), environment = null).getAttribute(AttributeKey.stringKey("deployment.environment")) == null)
      val off = SigNozTelemetry(SigNozTelemetryConfig(legacyEnvironmentAttribute = false))
      assert(resourceOf(off).getAttribute(AttributeKey.stringKey("deployment.environment")) == null)
    }
  }
}

sealed interface TestScope

@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class])
interface TestGraph : BootableGraph {
  val sigNozTelemetryConfig: SigNozTelemetryConfig
  val openTelemetryCustomizers: Set<OpenTelemetryCustomizer>
}

private val expectedConfig = SigNozTelemetryConfig(ingestionKey = Masked("wiring-key"), temporality = MetricsTemporality.Delta)

/** Config comes from src/test/resources/application.conf, as it would in an application. */
val SigNozTelemetryWiringTest by testSuite {
  testSuite("signoz config decoded by") {
    test("hoplite") {
      assert(hopliteConfigBinder(report = false, strict = true).config<SigNozTelemetryConfig>("telemetry.signoz") == expectedConfig)
    }

    test("kodein") {
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(openTelemetryModule)
        import(sigNozTelemetryModule)
      }.direct

      assert(di.instance<SigNozTelemetryConfig>() == expectedConfig)
      assert(di.instance<Set<OpenTelemetryCustomizer>>() == setOf<OpenTelemetryCustomizer>(di.instance<SigNozTelemetry>()))
      di.instance<OpenTelemetryService>().shutdown()
    }

    test("metro") {
      val graph = createGraph<TestGraph>()
      assert(graph.sigNozTelemetryConfig == expectedConfig)
      assert(graph.openTelemetryCustomizers.single() is SigNozTelemetry)
    }
  }
}
