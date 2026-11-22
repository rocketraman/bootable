package snippets.http

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.withHealthLifecycleController
import com.github.rocketraman.bootable.boot.defaultLifecycleControllers
import com.github.rocketraman.bootable.boot.readinessConfig
import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.HttpClientTelemetry
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.server.http.ktor.KtorHealth
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetryConfig
import com.github.rocketraman.bootable.server.http.ktor.excludeHealthFromTraces
import com.github.rocketraman.bootable.server.http.ktor.ktorHealthConfig
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.PrometheusTelemetry
import com.github.rocketraman.bootable.telemetry.otel.telemetryConfig
import io.ktor.server.cio.*

fun tracedServerApp(binder: ConfigBinder): Bootable {
  val health = HealthLifecycleController(config = readinessConfig(binder))
  // tag::server[]
  val prometheus = PrometheusTelemetry(binder.config("telemetry.prometheus"))  // a pulled backend, to scrape
  val ktorConfig = binder.config<KtorTelemetryConfig>("telemetry.ktor")
  val healthConfig = ktorHealthConfig(binder)
  val telemetry = OpenTelemetryService(
    telemetryConfig(binder),
    // neither the scrapes nor the health probes are traced
    listOf(prometheus, ktorConfig.excludeScrapeFromTraces(), healthConfig.excludeHealthFromTraces()),
  )
  val ktorTelemetry = KtorTelemetry(telemetry.openTelemetry, setOf(prometheus), ktorConfig)
  val ktorHealth = KtorHealth(health, healthConfig)

  return Bootable(
    setOf(
      telemetry,
      TracedGreetingServer(binder.config("server"), ktorHealth, ktorTelemetry),
      ktorTelemetry.listener(CIO),  // the scrape's own listener, if telemetry.ktor.hostPort is set
      ktorHealth.listener(CIO),
    ),
    defaultLifecycleControllers().withHealthLifecycleController(health),
  )
  // end::server[]
}

fun tracedClients(binder: ConfigBinder, telemetry: OpenTelemetryService): KtorClientService =
  // tag::client[]
  KtorClientService(
    binder.config("http.client"),
    CioClientEngine(),
    // a span for each request, and the HTTP client metrics
    HttpClientTelemetry(telemetry.openTelemetry, binder.config("telemetry.httpClient")),
  )
  // end::client[]
