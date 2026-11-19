package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.Masked
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.hopliteConfigModule
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import io.opentelemetry.api.OpenTelemetry
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

sealed interface TestScope

@DependencyGraph(
  TestScope::class,
  additionalScopes = [BootScope::class],
  // named rather than contributed: a @ContributesTo in test code trips the power-assert bug KT-83931 on the hint
  // file Metro generates for it
  bindingContainers = [TestTelemetryBindings::class],
)
interface TestGraph : BootableGraph {
  val openTelemetry: OpenTelemetry
  val telemetryConfig: TelemetryConfig
  val inMemoryTelemetry: InMemoryTelemetry
}

@BindingContainer
object TestTelemetryBindings {
  @Provides @SingleIn(TestScope::class) fun inMemoryTelemetry(): InMemoryTelemetry = InMemoryTelemetry()

  @Provides @IntoSet fun inMemory(telemetry: InMemoryTelemetry): OpenTelemetryCustomizer = telemetry
}

private val expectedConfig = TelemetryConfig(
  serviceName = "wiring-test",
  serviceVersion = "1.2.3",
  environment = "test",
  resourceAttributes = mapOf("service.namespace" to "bootable"),
  otlp = OtlpConfig(endpoint = "http://localhost:4318", headers = mapOf("api-key" to Masked("test-key")), compression = true, timeout = 2.seconds),
  traces = TracesConfig(sampleRatio = 0.5),
  metrics = MetricsConfig(interval = 30.seconds, runtime = false),
  registerGlobal = false,
)

/** Records a span and a metric through [openTelemetry], and checks they were recorded with the configured resource. */
private fun checkRecords(openTelemetry: OpenTelemetry, telemetry: InMemoryTelemetry) {
  openTelemetry.getMeter("wiring").counterBuilder("wired").build().add(1)
  val metric = telemetry.metrics.collectAllMetrics().single { it.name == "wired" }
  assert(metric.resource.getAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("service.name")) == "wiring-test")
}

/** Config comes from src/test/resources/application.conf, as it would in an application. */
val TelemetryWiringTest by testSuite {
  testSuite("telemetry config decoded by") {
    test("hoplite, with the environment from env.environment") {
      assert(telemetryConfig(hopliteConfigBinder(report = false)) == expectedConfig)
    }

    test("hoplite, with serviceInstanceId from an environment variable, as the Kubernetes downward API sets it") {
      val binder = hopliteConfigBinder(report = false, environmentVariables = { mapOf("TELEMETRY_SDK_SERVICEINSTANCEID" to "orders-0") })
      assert(telemetryConfig(binder, env = emptyMap()).serviceInstanceId == "orders-0")
    }

    test("hoplite in strict mode") {
      telemetryConfig(hopliteConfigBinder(report = false, strict = true))
    }

    test("preferring telemetry.sdk.environment to env.environment") {
      val binder = FakeConfigBinder(
        "telemetry.sdk" to TelemetryConfig(environment = "configured"),
        "env" to EnvironmentSection("from-env"),
        "build" to BuildSection(),
      )
      assert(telemetryConfig(binder, env = emptyMap()).environment == "configured")
    }

    test("with serviceVersion from build.version, the key of boot's BuildInfo") {
      val binder = FakeConfigBinder(
        "telemetry.sdk" to TelemetryConfig(),
        "env" to EnvironmentSection(),
        "build" to BuildSection("2.0.0"),
      )
      assert(telemetryConfig(binder, env = emptyMap()).serviceVersion == "2.0.0")
    }

    test("preferring telemetry.sdk.serviceVersion to build.version") {
      val binder = FakeConfigBinder(
        "telemetry.sdk" to TelemetryConfig(serviceVersion = "configured"),
        "env" to EnvironmentSection(),
        "build" to BuildSection("2.0.0"),
      )
      assert(telemetryConfig(binder, env = emptyMap()).serviceVersion == "configured")
    }

    test("as all defaults when the sections are absent") {
      val binder = FakeConfigBinder(
        "telemetry.sdk" to TelemetryConfig(),
        "env" to EnvironmentSection(),
        "build" to BuildSection(),
      )
      assert(telemetryConfig(binder, env = emptyMap()) == TelemetryConfig())
      assert(hopliteConfigBinder(report = false).configOf("absent.sdk", TelemetryConfig::class) == TelemetryConfig())
    }

    test("kodein") {
      val telemetry = InMemoryTelemetry()
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(openTelemetryModule)
        inBindSet<OpenTelemetryCustomizer> { add { singleton { telemetry } } }
      }.direct

      assert(di.instance<TelemetryConfig>() == expectedConfig)
      checkRecords(di.instance(), telemetry)
      val service = di.instance<Set<AppService>>().filterIsInstance<OpenTelemetryService>().single()
      assert(service.openTelemetry === di.instance<OpenTelemetry>())
      assert(di.instance<Set<MetricsSnapshotProvider>>().isEmpty())
    }

    test("metro") {
      val graph = createGraph<TestGraph>()

      assert(graph.telemetryConfig == expectedConfig)
      checkRecords(graph.openTelemetry, graph.inMemoryTelemetry)
      val service = graph.appServices.filterIsInstance<OpenTelemetryService>().single()
      assert(service.openTelemetry === graph.openTelemetry)
    }
  }
}

private class FakeConfigBinder(vararg sections: Pair<String, Any>) : ConfigBinder {
  private val sections = sections.toMap()

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> configOf(prefix: String, type: KClass<T>): T = sections.getValue(prefix) as T
}
