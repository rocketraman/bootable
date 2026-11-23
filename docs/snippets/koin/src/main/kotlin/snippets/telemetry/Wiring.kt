package snippets.telemetry

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.koin.serviceStartTracingModule
import org.koin.dsl.koinApplication

// tag::wire[]
fun appKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    openTelemetryModule(),        // the SDK, from telemetry.sdk, as an OpenTelemetry definition
    serviceStartTracingModule(),  // the start, as one trace
    // ... and the services that record telemetry, given get<OpenTelemetry>()
  )
}.koin

fun main() = boot {
  appKoin().get<Bootable>()
}
// end::wire[]
