package com.github.rocketraman.bootable.telemetry.otel.koin

import com.github.rocketraman.bootable.config.koin.config
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.GcpTelemetry
import com.github.rocketraman.bootable.telemetry.otel.GcpTelemetryConfig
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that connects the application's telemetry to Google Cloud: it adds a [GcpTelemetry], configured from
 * the `telemetry.gcp` config section, to the customizers of `openTelemetryModule()`. Include it alongside
 * `openTelemetryModule()`. Requires a `ConfigBinder` definition (e.g. from boot-config-hoplite's
 * `hopliteConfigModule()`).
 */
fun gcpTelemetryModule(): Module = module {
  config<GcpTelemetryConfig>("telemetry.gcp")
  single { GcpTelemetry(get()) }
  single<OpenTelemetryCustomizer>(named<GcpTelemetry>()) { get<GcpTelemetry>() }
}
