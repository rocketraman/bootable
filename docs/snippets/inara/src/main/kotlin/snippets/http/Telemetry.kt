package snippets.http

import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.HttpClientTelemetryModule
import com.github.rocketraman.bootable.client.http.ktor.KtorClientModule
import com.github.rocketraman.bootable.client.http.ktor.httpClientTelemetryModule
import com.github.rocketraman.bootable.client.http.ktor.ktorClientModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.server.http.ktor.KtorHealthModule
import com.github.rocketraman.bootable.server.http.ktor.KtorServerEngineModule
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetryModule
import com.github.rocketraman.bootable.server.http.ktor.ktorHealthModule
import com.github.rocketraman.bootable.server.http.ktor.ktorServerCioEngineModule
import com.github.rocketraman.bootable.server.http.ktor.ktorTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.PrometheusTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.prometheusTelemetryModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

// tag::server[]
@DIModule(composed = true)
interface TracedServerAppModule :
  ConfigModule, BootableModule, OpenTelemetryModule, PrometheusTelemetryModule,
  KtorServerEngineModule, KtorTelemetryModule, KtorHealthModule {
  val serverConfig: ServerConfig
  @AppServices val tracedServer: TracedGreetingServer
}

fun tracedServerAppModule() = TracedServerAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),              // the application's OpenTelemetry, from boot-telemetry-otel
  prometheusTelemetryModule = prometheusTelemetryModule(),  // a pulled backend, to scrape
  ktorTelemetryModule = ktorTelemetryModule(),              // KtorTelemetry, from telemetry.ktor
  ktorHealthModule = ktorHealthModule(),
  // the engine of the scrape's and the probes' own listeners, if their hostPort is set
  ktorServerEngineModule = ktorServerCioEngineModule(),
  serverConfig = config("server"),
  tracedServer = Binding.single { TracedGreetingServer(serverConfig, ktorHealth, ktorTelemetry) },
)
// end::server[]

// tag::client[]
@DIModule(composed = true)
interface TracedClientAppModule :
  ConfigModule, BootableModule, OpenTelemetryModule, KtorClientModule, ClientModule, HttpClientTelemetryModule

fun tracedClientAppModule() = TracedClientAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),
  ktorClientModule = ktorClientModule(CioClientEngine()),
  clientModule = clientModule(),
  // requires the KtorClientModule and an OpenTelemetryApiModule: a span for each request, and the HTTP client metrics
  httpClientTelemetryModule = httpClientTelemetryModule(),
)
// end::client[]
