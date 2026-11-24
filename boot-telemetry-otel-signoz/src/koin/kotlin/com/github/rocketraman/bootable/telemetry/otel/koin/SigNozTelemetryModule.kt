package com.github.rocketraman.bootable.telemetry.otel.koin

import com.github.rocketraman.bootable.config.koin.config
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.SigNozTelemetry
import com.github.rocketraman.bootable.telemetry.otel.SigNozTelemetryConfig
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that connects the application's telemetry to SigNoz: it adds a [SigNozTelemetry], configured from
 * the `telemetry.signoz` config section, to the customizers of `openTelemetryModule()`. Include it alongside
 * `openTelemetryModule()`. Requires a `ConfigBinder` definition (e.g. from boot-config-hoplite's
 * `hopliteConfigModule()`).
 */
fun sigNozTelemetryModule(): Module = module {
  config<SigNozTelemetryConfig>("telemetry.signoz")
  single { SigNozTelemetry(get()) }
  single<OpenTelemetryCustomizer>(named<SigNozTelemetry>()) { get<SigNozTelemetry>() }
}
