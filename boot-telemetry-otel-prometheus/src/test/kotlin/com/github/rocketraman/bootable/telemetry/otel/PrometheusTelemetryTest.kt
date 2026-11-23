@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.koin.bootableModule as koinBootableModule
import com.github.rocketraman.bootable.boot.setOfAppServices
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule as koinHopliteConfigModule
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule as koinOpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.koin.prometheusTelemetryModule as koinPrometheusTelemetryModule
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.InstrumentSelector
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.metrics.View
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.inara.DIModule
import org.kodein.di.instance
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import org.koin.dsl.koinApplication

sealed interface TestScope

@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class])
interface TestGraph : BootableGraph {
  val openTelemetry: OpenTelemetry
  val metricsSnapshotProviders: Set<MetricsSnapshotProvider>
  val openTelemetryCustomizers: Set<OpenTelemetryCustomizer>
}

/** An application's module, composed as an Inara application composes it. */
@DIModule(composed = true)
interface InaraTestModule : ConfigModule, BootableModule, OpenTelemetryModule, PrometheusTelemetryModule

private val CHANNEL = AttributeKey.stringKey("channel")
private val URL_PATH = AttributeKey.stringKey("url.path")

private suspend fun withPrometheus(
  config: PrometheusTelemetryConfig = PrometheusTelemetryConfig(),
  customizers: List<OpenTelemetryCustomizer> = emptyList(),
  block: (OpenTelemetry, PrometheusTelemetry) -> Unit,
) {
  val prometheus = PrometheusTelemetry(config)
  val service = OpenTelemetryService(
    TelemetryConfig(serviceName = "orders", metrics = MetricsConfig(runtime = false), registerGlobal = false),
    listOf(prometheus) + customizers,
  )
  try {
    block(service.openTelemetry, prometheus)
  } finally {
    service.shutdown()
  }
}

private fun OpenTelemetry.recordOrders() =
  getMeter("orders").counterBuilder("orders.placed").setDescription("Orders placed").build()
    .add(2, Attributes.of(CHANNEL, "web"))

/** Records a counter through the application's OpenTelemetry and checks that it is in the scrape, as configured. */
private suspend fun checkScrape(openTelemetry: OpenTelemetry, providers: Set<MetricsSnapshotProvider>) {
  openTelemetry.recordOrders()
  val body = providers.single().snapshot(null).body.decodeToString()
  // resourceAttributesAsLabels = [service.name], and no scope labels or target_info
  assert(body.lines().any { it.startsWith("orders_placed_total{") && """service_name="prometheus-test"""" in it })
  assert("otel_scope_name" !in body)
  assert("target_info" !in body)
}

val PrometheusTelemetryTest by testSuite {
  testSuite("the scrape") {
    test("has every metric recorded through the SDK") {
      withPrometheus { openTelemetry, prometheus ->
        openTelemetry.recordOrders()
        val body = prometheus.snapshot(null).body.decodeToString()
        assert(body.lines().any { it.startsWith("orders_placed_total{") && """channel="web"""" in it && it.endsWith(" 2.0") })
        assert("# HELP orders_placed_total Orders placed" in body)
        // the resource, by default as target_info, rather than on every series
        assert(body.lines().any { it.startsWith("target_info{") && """service_name="orders"""" in it })
      }
    }

    test("is the Prometheus text format by default") {
      withPrometheus { openTelemetry, prometheus ->
        openTelemetry.recordOrders()
        val scrape = prometheus.snapshot("text/plain")
        assert(scrape.contentType.startsWith("text/plain; version=0.0.4"))
        assert("# EOF" !in scrape.body.decodeToString())
      }
    }

    test("is OpenMetrics when the scraper accepts it, as Prometheus does") {
      withPrometheus { openTelemetry, prometheus ->
        openTelemetry.recordOrders()
        val accept = "application/openmetrics-text;version=1.0.0;q=0.5,text/plain;version=0.0.4;q=0.4,*/*;q=0.1"
        val scrape = prometheus.snapshot(accept)
        assert(scrape.contentType.startsWith("application/openmetrics-text"))
        assert(scrape.body.decodeToString().trimEnd().endsWith("# EOF"))
      }
    }

    test("has exemplars with only the trace and span ids, and no warning that their attributes are too long") {
      // like the HTTP server instrumentation's request duration: the metric keeps fewer attributes than it's recorded with
      val keepChannel = object : OpenTelemetryCustomizer {
        override fun customizeMeterProvider(builder: SdkMeterProviderBuilder) {
          builder.registerView(
            InstrumentSelector.builder().setName("orders.duration").build(),
            View.builder().setAttributeFilter(setOf(CHANNEL.key)).build(),
          )
        }
      }
      val warnings = mutableListOf<String>()
      val handler = object : Handler() {
        override fun publish(record: LogRecord) {
          if (record.level == Level.WARNING) warnings += record.message
        }
        override fun flush() {}
        override fun close() {}
      }
      val converterLogger = Logger.getLogger("io.opentelemetry.exporter.prometheus.Otel2PrometheusConverter")
      converterLogger.addHandler(handler)
      try {
        withPrometheus(customizers = listOf(keepChannel)) { openTelemetry, prometheus ->
          val span = openTelemetry.getTracer("orders").spanBuilder("checkout").startSpan()
          span.makeCurrent().use {
            openTelemetry.getMeter("orders").histogramBuilder("orders.duration").build()
              .record(0.25, Attributes.of(CHANNEL, "web", URL_PATH, "/orders/" + "x".repeat(100)))
          }
          span.end()

          val body = prometheus.snapshot("application/openmetrics-text").body.decodeToString()
          val exemplars = body.lines().filter { it.startsWith("orders_duration_bucket{") && " # {" in it }
          assert(exemplars.isNotEmpty())
          exemplars.forEach {
            assert("trace_id=\"${span.spanContext.traceId}\"" in it && "span_id=\"${span.spanContext.spanId}\"" in it)
            assert("url_path" !in it)
          }
          assert(warnings.isEmpty()) { "warnings: $warnings" }
        }
      } finally {
        converterLogger.removeHandler(handler)
      }
    }

    test("is never Prometheus's protobuf format") {
      withPrometheus { _, prometheus ->
        val accept = "application/vnd.google.protobuf;proto=io.prometheus.client.MetricFamily;encoding=delimited"
        assert(prometheus.snapshot(accept).contentType.startsWith("text/plain"))
      }
    }
  }

  testSuite("prometheus config") {
    test("is decoded by hoplite") {
      val config = hopliteConfigBinder(report = false, strict = true).config<PrometheusTelemetryConfig>("telemetry.prometheus")
      assert(config == PrometheusTelemetryConfig(otelScopeLabels = false, targetInfo = false, resourceAttributesAsLabels = setOf("service.name")))
    }

    test("is all defaults when the section is absent") {
      assert(hopliteConfigBinder(report = false).config<PrometheusTelemetryConfig>("absent.prometheus") == PrometheusTelemetryConfig())
    }
  }

  testSuite("wired by") {
    test("kodein") {
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(openTelemetryModule)
        import(prometheusTelemetryModule)
      }.direct

      val prometheus = di.instance<PrometheusTelemetry>()
      assert(di.instance<Set<OpenTelemetryCustomizer>>() == setOf<OpenTelemetryCustomizer>(prometheus))
      checkScrape(di.instance(), di.instance())
      di.instance<Set<AppService>>().filterIsInstance<OpenTelemetryService>().single().shutdown()
    }

    test("koin") {
      val koin = koinApplication {
        modules(koinBootableModule(), koinHopliteConfigModule(), koinOpenTelemetryModule(), koinPrometheusTelemetryModule())
      }.koin

      try {
        val prometheus = koin.get<PrometheusTelemetry>()
        assert(koin.getAll<OpenTelemetryCustomizer>().toSet() == setOf<OpenTelemetryCustomizer>(prometheus))
        checkScrape(koin.get(), koin.getAll<MetricsSnapshotProvider>().toSet())
        koin.getAll<AppService>().filterIsInstance<OpenTelemetryService>().single().shutdown()
      } finally {
        koin.close()
      }
    }

    test("metro") {
      val graph = createGraph<TestGraph>()

      assert(graph.openTelemetryCustomizers.single() is PrometheusTelemetry)
      checkScrape(graph.openTelemetry, graph.metricsSnapshotProviders)
      graph.appServices.filterIsInstance<OpenTelemetryService>().single().shutdown()
    }

    test("inara") {
      val module = InaraTestModule(
        configModule = hopliteConfigModule(),
        bootableModule = bootableModule(),
        openTelemetryModule = openTelemetryModule(),
        prometheusTelemetryModule = prometheusTelemetryModule(),
      )

      assert(module.setOfOpenTelemetryCustomizers() == setOf<OpenTelemetryCustomizer>(module.prometheusTelemetry))
      checkScrape(module.openTelemetry, module.setOfMetricsSnapshotProviders())
      module.setOfAppServices().filterIsInstance<OpenTelemetryService>().single().shutdown()
    }
  }
}
