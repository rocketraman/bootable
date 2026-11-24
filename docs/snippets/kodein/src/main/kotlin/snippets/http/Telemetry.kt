package snippets.http

import com.github.rocketraman.bootable.config.bindConfig
import com.github.rocketraman.bootable.server.http.ktor.ktorServerCioEngineModule
import com.github.rocketraman.bootable.boot.bindAppService
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.client.http.ktor.httpClientTelemetryModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.server.http.ktor.ktorHealthModule
import com.github.rocketraman.bootable.server.http.ktor.ktorTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.prometheusTelemetryModule
import org.kodein.di.DI
import org.kodein.di.instance
import org.kodein.di.new
import org.kodein.di.singleton

fun tracedServerDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  // tag::server[]
  import(openTelemetryModule)        // the application's OpenTelemetry, from boot-telemetry-otel
  import(prometheusTelemetryModule)  // a pulled backend, to scrape
  import(ktorTelemetryModule)        // KtorTelemetry, from telemetry.ktor, after openTelemetryModule
  import(ktorHealthModule)
  bindConfig<ServerConfig>("server")
  bindAppService { singleton { new(::TracedGreetingServer) } }
  // the engine of the scrape's and the probes' own listeners, if their hostPort is set
  import(ktorServerCioEngineModule)
  // end::server[]
}

fun tracedClientDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  // tag::client[]
  import(openTelemetryModule)
  import(clientModule)               // ktorClientModule, and the engine
  import(httpClientTelemetryModule)  // after both: a span for each request, and the HTTP client metrics
  // end::client[]
}
