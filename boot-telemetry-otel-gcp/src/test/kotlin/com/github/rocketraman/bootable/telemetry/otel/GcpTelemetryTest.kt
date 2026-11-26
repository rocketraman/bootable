@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.koin.bootableModule as koinBootableModule
import com.github.rocketraman.bootable.clock.ClockModule
import com.github.rocketraman.bootable.clock.clockModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule as koinHopliteConfigModule
import com.github.rocketraman.bootable.telemetry.otel.koin.gcpTelemetryModule as koinGcpTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule as koinOpenTelemetryModule
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.sun.net.httpserver.HttpServer
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.inara.DIModule
import org.kodein.di.instance
import java.net.InetSocketAddress
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import org.koin.dsl.koinApplication

private val cloudRun = Attributes.builder()
  .put("cloud.provider", "gcp")
  .put("cloud.platform", "gcp_cloud_run")
  .put("cloud.account.id", "detected-project")
  .put("cloud.region", "europe-west1")
  .put("faas.name", "orders")
  .put("faas.version", "orders-00042-abc")
  .build()

private fun fakeCredentials(quotaProject: String? = null): GoogleCredentials =
  GoogleCredentials.newBuilder()
    .setAccessToken(AccessToken("test-token", Date(System.currentTimeMillis() + 3_600_000)))
    .apply { quotaProject?.let { setQuotaProjectId(it) } }
    .build()

private fun service(gcp: GcpTelemetry, endpoint: String? = null) = OpenTelemetryService(
  TelemetryConfig(serviceName = "orders-api", otlp = OtlpConfig(endpoint = endpoint), metrics = MetricsConfig(runtime = false), registerGlobal = false),
  listOf(gcp),
)

/** The headers of every span export to a local OTLP receiver, after one span is recorded and the SDK shut down. */
private suspend fun exportedHeaders(gcp: GcpTelemetry): List<Map<String, String>> {
  val requests = CopyOnWriteArrayList<Map<String, String>>()
  val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  server.createContext("/v1/traces") { exchange ->
    requests += exchange.requestHeaders.mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString() }
    exchange.sendResponseHeaders(200, -1)
    exchange.close()
  }
  server.start()
  try {
    val service = service(gcp, "http://127.0.0.1:${server.address.port}")
    service.openTelemetry.getTracer("test").spanBuilder("checkout").startSpan().end()
    service.shutdown()
  } finally {
    server.stop(0)
  }
  return requests
}

val GcpTelemetryTest by testSuite {
  testSuite("the resource") {
    test("has the detected platform's attributes") {
      val resource = service(GcpTelemetry(detect = { cloudRun })).resource
      assert(resource.getAttribute(AttributeKey.stringKey("cloud.platform")) == "gcp_cloud_run")
      assert(resource.getAttribute(AttributeKey.stringKey("faas.version")) == "orders-00042-abc")
    }

    test("keeps the application's own attributes over the detected ones") {
      val detected = Attributes.builder().putAll(cloudRun).put("service.name", "detected").build()
      val resource = service(GcpTelemetry(detect = { detected })).resource
      assert(resource.getAttribute(AttributeKey.stringKey("service.name")) == "orders-api")
    }

    test("is left alone when detection is off") {
      val resource = service(GcpTelemetry(GcpTelemetryConfig(detectResource = false), detect = { error("not called") })).resource
      assert(resource.getAttribute(AttributeKey.stringKey("cloud.platform")) == null)
    }
  }

  testSuite("export") {
    test("is authenticated when it goes to Google, and not when it goes to a collector") {
      val gcp = GcpTelemetry()
      assert(gcp.authenticates("https://telemetry.googleapis.com/v1/traces"))
      assert(!gcp.authenticates("http://otel-collector:4318/v1/traces"))
      assert(!gcp.authenticates("http://notgoogleapis.com/v1/traces"))
      assert(GcpTelemetry(GcpTelemetryConfig(authenticate = true)).authenticates("http://otel-collector:4318/v1/traces"))
      assert(!GcpTelemetry(GcpTelemetryConfig(authenticate = false)).authenticates("https://telemetry.googleapis.com"))
    }

    test("carries an access token, and the detected project as the quota project") {
      val gcp = GcpTelemetry(GcpTelemetryConfig(authenticate = true), detect = { cloudRun }, credentials = { fakeCredentials() })
      val headers = exportedHeaders(gcp).single()
      assert(headers["authorization"] == "Bearer test-token")
      assert(headers["x-goog-user-project"] == "detected-project")
    }

    test("uses the configured quota project, over the credentials' own") {
      val gcp = GcpTelemetry(
        GcpTelemetryConfig(authenticate = true, projectId = "configured"),
        detect = { cloudRun },
        credentials = { fakeCredentials(quotaProject = "credentials-project") },
      )
      assert(exportedHeaders(gcp).single()["x-goog-user-project"] == "configured")
    }

    test("uses the credentials' quota project, over the detected one") {
      val gcp = GcpTelemetry(GcpTelemetryConfig(authenticate = true), detect = { cloudRun }, credentials = { fakeCredentials("credentials-project") })
      assert(exportedHeaders(gcp).single()["x-goog-user-project"] == "credentials-project")
    }

    test("loads no credentials when it isn't authenticated") {
      val gcp = GcpTelemetry(GcpTelemetryConfig(detectResource = false), credentials = { error("no credentials here") })
      assert(exportedHeaders(gcp).single()["authorization"] == null)
    }

    test("fails at boot, rather than on every export, without credentials") {
      val gcp = GcpTelemetry(GcpTelemetryConfig(authenticate = true, detectResource = false), credentials = { error("no credentials here") })
      val failure = runCatching { service(gcp, "http://127.0.0.1:1") }.exceptionOrNull()
      assert(failure?.message == "no credentials here")
    }
  }
}

sealed interface TestScope

@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class])
interface TestGraph : BootableGraph {
  val gcpTelemetryConfig: GcpTelemetryConfig
  val openTelemetryCustomizers: Set<OpenTelemetryCustomizer>
}

/** An application's module, composed as an Inara application composes it. */
@DIModule(composed = true)
interface InaraTestModule : ConfigModule, BootableModule, ClockModule, OpenTelemetryModule, GcpTelemetryModule

private val expectedConfig = GcpTelemetryConfig(detectResource = false, projectId = "wiring-project")

/** Config comes from src/test/resources/application.conf, as it would in an application. */
val GcpTelemetryWiringTest by testSuite {
  testSuite("gcp config decoded by") {
    test("hoplite") {
      assert(hopliteConfigBinder(report = false, strict = true).config<GcpTelemetryConfig>("telemetry.gcp") == expectedConfig)
    }

    test("kodein") {
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(openTelemetryModule)
        import(gcpTelemetryModule)
      }.direct

      assert(di.instance<GcpTelemetryConfig>() == expectedConfig)
      assert(di.instance<Set<OpenTelemetryCustomizer>>() == setOf<OpenTelemetryCustomizer>(di.instance<GcpTelemetry>()))
      di.instance<OpenTelemetryService>().shutdown()
    }

    test("koin") {
      val koin = koinApplication {
        modules(koinBootableModule(), koinHopliteConfigModule(), koinOpenTelemetryModule(), koinGcpTelemetryModule())
      }.koin

      try {
        assert(koin.get<GcpTelemetryConfig>() == expectedConfig)
        assert(koin.getAll<OpenTelemetryCustomizer>().toSet() == setOf<OpenTelemetryCustomizer>(koin.get<GcpTelemetry>()))
        koin.get<OpenTelemetryService>().shutdown()
      } finally {
        koin.close()
      }
    }

    test("metro") {
      val graph = createGraph<TestGraph>()
      assert(graph.gcpTelemetryConfig == expectedConfig)
      assert(graph.openTelemetryCustomizers.single() is GcpTelemetry)
    }

    test("inara") {
      val module = InaraTestModule(
        configModule = hopliteConfigModule(),
        bootableModule = bootableModule(),
        clockModule = clockModule(),
        openTelemetryModule = openTelemetryModule(),
        gcpTelemetryModule = gcpTelemetryModule(),
      )

      assert(module.gcpTelemetryConfig == expectedConfig)
      assert(module.setOfOpenTelemetryCustomizers() == setOf<OpenTelemetryCustomizer>(module.gcpTelemetry))
      module.openTelemetryService.shutdown()
    }
  }
}
