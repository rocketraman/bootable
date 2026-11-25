package snippets.http

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.client.http.ktor.koin.httpClientTelemetryModule
import com.github.rocketraman.bootable.config.koin.config
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import com.github.rocketraman.bootable.server.http.ktor.koin.ktorHealthModule
import com.github.rocketraman.bootable.server.http.ktor.koin.ktorServerCioEngineModule
import com.github.rocketraman.bootable.server.http.ktor.koin.ktorTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.koin.prometheusTelemetryModule
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

fun tracedServerKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    // tag::server[]
    openTelemetryModule(),        // the application's OpenTelemetry, from boot-telemetry-otel
    prometheusTelemetryModule(),  // a pulled backend, to scrape
    ktorTelemetryModule(),        // KtorTelemetry, from telemetry.ktor, over openTelemetryModule()
    ktorHealthModule(),
    module {
      config<ServerConfig>("server")
      single<AppService>(named<TracedGreetingServer>()) { TracedGreetingServer(get(), get(), get()) }
    },
    // the engine of the scrape's and the probes' own listeners, if their hostPort is set
    ktorServerCioEngineModule(),
    // end::server[]
  )
}.koin

fun tracedClientKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    // tag::client[]
    openTelemetryModule(),
    clientModule,               // ktorClientModule(), and the engine
    httpClientTelemetryModule(),  // a span for each request, and the HTTP client metrics
    // end::client[]
  )
}.koin
