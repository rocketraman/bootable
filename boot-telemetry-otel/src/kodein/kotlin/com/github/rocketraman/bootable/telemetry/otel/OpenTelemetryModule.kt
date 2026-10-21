package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.AppService
import io.opentelemetry.api.OpenTelemetry
import org.kodein.di.DI
import org.kodein.di.bindSet
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that binds the application's [OpenTelemetry] and registers its [OpenTelemetryService] into the
 * app-service set, so the last telemetry is exported, and the SDK shut down, when the application stops. Its
 * [TelemetryConfig] is decoded by [telemetryConfig], from the `telemetry.sdk` config section. Requires a
 * `ConfigBinder` binding (e.g. from boot-config-hoplite's `hopliteConfigModule`).
 *
 * It opens two sets, so import it before the modules that add to them:
 * - `Set<OpenTelemetryCustomizer>`: how other modules, such as `prometheusTelemetryModule` from
 *   `boot-telemetry-otel-prometheus`, plug into the SDK. Add your own for what [TelemetryConfig] doesn't cover:
 *   ```
 *   inBindSet<OpenTelemetryCustomizer> { add { singleton { excludeUrlPathsFromTraces(setOf("/health")) } } }
 *   ```
 * - `Set<MetricsSnapshotProvider>`: the scrape endpoints of pulled metrics backends, for an HTTP integration to serve.
 */
val openTelemetryModule by DI.Module {
  bindSet<OpenTelemetryCustomizer>()
  bindSet<MetricsSnapshotProvider>()
  bindSingleton { telemetryConfig(instance()) }
  bindSingleton { OpenTelemetryService(instance(), instance<Set<OpenTelemetryCustomizer>>()) }
  bindSingleton<OpenTelemetry> { instance<OpenTelemetryService>().openTelemetry }
  inBindSet<AppService> { add { singleton { instance<OpenTelemetryService>() } } }
}
