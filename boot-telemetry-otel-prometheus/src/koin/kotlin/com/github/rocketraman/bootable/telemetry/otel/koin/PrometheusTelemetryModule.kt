package com.github.rocketraman.bootable.telemetry.otel.koin

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.telemetry.otel.MetricsSnapshotProvider
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.PrometheusTelemetry
import com.github.rocketraman.bootable.telemetry.otel.PrometheusTelemetryConfig
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that exposes the application's metrics for Prometheus to scrape: it adds a [PrometheusTelemetry],
 * configured from the `telemetry.prometheus` config section, to the customizers of `openTelemetryModule()`, which
 * registers its metric reader with the SDK, and to the metrics snapshot providers an HTTP integration serves. Include
 * it alongside `openTelemetryModule()`. Requires a `ConfigBinder` definition (e.g. from boot-config-hoplite's
 * `hopliteConfigModule()`).
 */
fun prometheusTelemetryModule(): Module = module {
  single<PrometheusTelemetryConfig> { get<ConfigBinder>().config("telemetry.prometheus") }
  single { PrometheusTelemetry(get()) }
  single<OpenTelemetryCustomizer>(named<PrometheusTelemetry>()) { get<PrometheusTelemetry>() }
  single<MetricsSnapshotProvider>(named<PrometheusTelemetry>()) { get<PrometheusTelemetry>() }
}
