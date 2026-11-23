package snippets.telemetry

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import com.github.rocketraman.bootable.server.http.ktor.koin.ktorServerCioEngineModule
import com.github.rocketraman.bootable.server.http.ktor.koin.ktorTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.koin.gcpTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.koin.micrometerBridgeModule
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.koin.prometheusTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.koin.sigNozTelemetryModule
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

fun prometheusKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    // tag::prometheus[]
    openTelemetryModule(),
    prometheusTelemetryModule(),  // registers its reader, and provides the scrape
    ktorTelemetryModule(),        // KtorTelemetry, which serves it; no traces of the scrapes
    module {
      single { get<ConfigBinder>().config<StatusServerConfig>("server") }
      single<AppService>(named<StatusServer>()) { StatusServer(get(), get()) }  // serves the scrape at /metrics
    },
    ktorServerCioEngineModule(),  // or on a port of its own, telemetry.ktor.hostPort, on this engine
    // end::prometheus[]
  )
}.koin

fun sigNozKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    // tag::signoz[]
    openTelemetryModule(),
    sigNozTelemetryModule(),  // from telemetry.signoz
    // end::signoz[]
  )
}.koin

fun gcpKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    // tag::gcp[]
    openTelemetryModule(),
    gcpTelemetryModule(),  // from telemetry.gcp
    // end::gcp[]
  )
}.koin

fun micrometerBridgeKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    // tag::micrometer-bridge[]
    openTelemetryModule(),
    micrometerBridgeModule(),  // a MeterRegistry definition, over the application's OpenTelemetry
    module {
      // e.g. a library's Micrometer binder: the executor's metrics, exported with the rest
      single<ExecutorService> { ExecutorServiceMetrics.monitor(get(), Executors.newFixedThreadPool(4), "work") }
    },
    // end::micrometer-bridge[]
  )
}.koin
