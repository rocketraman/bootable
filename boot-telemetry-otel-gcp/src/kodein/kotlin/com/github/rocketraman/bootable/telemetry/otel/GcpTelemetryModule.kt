package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that connects the application's telemetry to Google Cloud: it adds a [GcpTelemetry], configured from
 * the `telemetry.gcp` config section, to the customizers of `openTelemetryModule`. Import it after
 * `openTelemetryModule`, which opens that set. Requires a `ConfigBinder` binding (e.g. from boot-config-hoplite's
 * `hopliteConfigModule`).
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val gcpTelemetryModule by DI.Module {
  bindSingleton<GcpTelemetryConfig> { instance<ConfigBinder>().config("telemetry.gcp") }
  bindSingleton { GcpTelemetry(instance()) }
  inBindSet<OpenTelemetryCustomizer> { add { singleton { instance<GcpTelemetry>() } } }
}
