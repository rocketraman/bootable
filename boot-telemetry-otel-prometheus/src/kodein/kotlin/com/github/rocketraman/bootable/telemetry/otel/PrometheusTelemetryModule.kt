package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that exposes the application's metrics for Prometheus to scrape: it adds a [PrometheusTelemetry],
 * configured from the `telemetry.prometheus` config section, to the customizers of `openTelemetryModule`, which
 * registers its metric reader with the SDK, and to the metrics snapshot providers an HTTP integration serves. Import it after
 * `openTelemetryModule`, which opens those sets. Requires a `ConfigBinder` binding (e.g. from boot-config-hoplite's
 * `hopliteConfigModule`).
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val prometheusTelemetryModule by DI.Module {
  bindSingleton<PrometheusTelemetryConfig> { instance<ConfigBinder>().config("telemetry.prometheus") }
  bindSingleton { PrometheusTelemetry(instance()) }
  inBindSet<OpenTelemetryCustomizer> { add { singleton { instance<PrometheusTelemetry>() } } }
  inBindSet<MetricsSnapshotProvider> { add { singleton { instance<PrometheusTelemetry>() } } }
}
