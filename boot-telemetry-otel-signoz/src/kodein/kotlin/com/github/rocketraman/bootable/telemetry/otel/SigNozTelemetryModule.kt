package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that connects the application's telemetry to SigNoz: it adds a [SigNozTelemetry], configured from
 * the `telemetry.signoz` config section, to the customizers of `openTelemetryModule`. Import it after
 * `openTelemetryModule`, which opens that set. Requires a `ConfigBinder` binding (e.g. from boot-config-hoplite's
 * `hopliteConfigModule`).
 */
val sigNozTelemetryModule by DI.Module {
  bindSingleton<SigNozTelemetryConfig> { instance<ConfigBinder>().config("telemetry.signoz") }
  bindSingleton { SigNozTelemetry(instance()) }
  inBindSet<OpenTelemetryCustomizer> { add { singleton { instance<SigNozTelemetry>() } } }
}
