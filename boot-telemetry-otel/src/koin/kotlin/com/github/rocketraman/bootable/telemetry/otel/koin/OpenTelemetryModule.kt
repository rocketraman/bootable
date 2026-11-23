package com.github.rocketraman.bootable.telemetry.otel.koin

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.telemetry.otel.MetricsSnapshotProvider
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.TelemetryConfig
import com.github.rocketraman.bootable.telemetry.otel.telemetryConfig
import io.opentelemetry.api.OpenTelemetry
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * A Koin module that defines the application's [OpenTelemetry] and adds its [OpenTelemetryService] to the app
 * services, so the last telemetry is exported, and the SDK shut down, when the application stops. Its
 * [TelemetryConfig] is decoded by [telemetryConfig], from the `telemetry.sdk` config section. Requires a
 * `ConfigBinder` definition (e.g. from boot-config-hoplite's `hopliteConfigModule()`).
 *
 * A `kotlin.time.Clock` definition, if there is one, is the SDK's clock; otherwise it is `Clock.System`. Define one,
 * e.g. a fixed clock in a test, to set the time of the spans and measurements.
 *
 * Other modules plug into it through two types, each definition with a qualifier of its own:
 * - [OpenTelemetryCustomizer]: how other modules, such as `prometheusTelemetryModule()` from
 *   `boot-telemetry-otel-prometheus`, plug into the SDK. Define your own for what [TelemetryConfig] doesn't cover:
 *   ```
 *   single<OpenTelemetryCustomizer>(named("health")) { excludeUrlPathsFromTraces(setOf("/health")) }
 *   ```
 * - [MetricsSnapshotProvider]: the scrape endpoints of pulled metrics backends, for an HTTP integration to serve.
 */
fun openTelemetryModule(): Module = module {
  single { telemetryConfig(get()) }
  single { OpenTelemetryService(get(), getAll<OpenTelemetryCustomizer>(), getOrNull<Clock>() ?: Clock.System) }
  single<OpenTelemetry> { get<OpenTelemetryService>().openTelemetry }
  single<AppService>(named<OpenTelemetryService>()) { get<OpenTelemetryService>() }
}
