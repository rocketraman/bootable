@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.koin.bootableModule as koinBootableModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule as koinHopliteConfigModule
import com.github.rocketraman.bootable.telemetry.otel.koin.micrometerBridgeModule as koinMicrometerBridgeModule
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule as koinOpenTelemetryModule
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.inBindSet
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.instance
import org.kodein.di.singleton
import java.time.Duration
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** Reads every metric the SDK records, as a test's window onto it. */
class InMemoryMetrics : OpenTelemetryCustomizer {
  val reader: InMemoryMetricReader = InMemoryMetricReader.create()

  override fun customizeMeterProvider(builder: SdkMeterProviderBuilder) {
    builder.registerMetricReader(reader)
  }

  fun metric(name: String): MetricData = reader.collectAllMetrics().single { it.name == name }
}

sealed interface TestScope

@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class], bindingContainers = [TestMetricsBindings::class])
interface TestGraph : BootableGraph {
  val meterRegistry: MeterRegistry
  val inMemoryMetrics: InMemoryMetrics
}

@BindingContainer
object TestMetricsBindings {
  @Provides @SingleIn(TestScope::class) fun inMemoryMetrics(): InMemoryMetrics = InMemoryMetrics()

  @Provides @IntoSet fun inMemory(metrics: InMemoryMetrics): OpenTelemetryCustomizer = metrics
}

/** An application's module, composed as an Inara application composes it, with the SDK's metrics kept in memory. */
@DIModule(composed = true)
interface InaraTestModule : ConfigModule, BootableModule, OpenTelemetryModule, MicrometerBridgeModule {
  @OpenTelemetryCustomizers val inMemoryMetrics: InMemoryMetrics
}

/** Records a Micrometer counter, and checks the SDK has it, with the application's resource. */
private fun checkBridged(registry: MeterRegistry, metrics: InMemoryMetrics) {
  registry.counter("bridged.requests", "route", "/orders").increment(2.0)
  val metric = metrics.metric("bridged.requests")
  assert(metric.resource.getAttribute(AttributeKey.stringKey("service.name")) == "bridge-test")
  val point = metric.doubleSumData.points.single()
  assert(point.value == 2.0)
  assert(point.attributes.get(AttributeKey.stringKey("route")) == "/orders")
}

val MicrometerBridgeTest by testSuite {
  test("Micrometer meters are recorded into the OpenTelemetry SDK") {
    val metrics = InMemoryMetrics()
    val service = OpenTelemetryService(
      TelemetryConfig(metrics = MetricsConfig(runtime = false), registerGlobal = false),
      listOf(metrics),
    )
    val registry = openTelemetryMeterRegistry(service.openTelemetry)

    registry.counter("orders.placed", "channel", "web").increment(3.0)
    registry.timer("orders.checkout").record(Duration.ofMillis(250))

    assert(metrics.metric("orders.placed").doubleSumData.points.single().value == 3.0)
    val timer = metrics.metric("orders.checkout").histogramData.points.single()
    assert(timer.count == 1L)
    assert(timer.sum == 0.25)
    service.shutdown()
  }

  testSuite("wired by") {
    test("kodein") {
      val metrics = InMemoryMetrics()
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(openTelemetryModule)
        import(micrometerBridgeModule)
        inBindSet<OpenTelemetryCustomizer> { add { singleton { metrics } } }
      }.direct

      checkBridged(di.instance(), metrics)
    }

    test("koin") {
      val metrics = InMemoryMetrics()
      val koin = koinApplication {
        modules(
          koinBootableModule(),
          koinHopliteConfigModule(),
          koinOpenTelemetryModule(),
          koinMicrometerBridgeModule(),
          module { single<OpenTelemetryCustomizer>(named("test")) { metrics } },
        )
      }.koin

      try {
        checkBridged(koin.get(), metrics)
      } finally {
        koin.close()
      }
    }

    test("metro") {
      val graph = createGraph<TestGraph>()
      checkBridged(graph.meterRegistry, graph.inMemoryMetrics)
    }

    test("inara") {
      val module = InaraTestModule(
        configModule = hopliteConfigModule(),
        bootableModule = bootableModule(),
        openTelemetryModule = openTelemetryModule(),
        micrometerBridgeModule = micrometerBridgeModule(),
        inMemoryMetrics = Binding.single { InMemoryMetrics() },
      )
      checkBridged(module.meterRegistry, module.inMemoryMetrics)
    }
  }
}
