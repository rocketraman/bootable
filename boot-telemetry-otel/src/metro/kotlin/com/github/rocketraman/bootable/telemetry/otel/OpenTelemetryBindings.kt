package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.telemetry.otel.telemetryConfig as decodeTelemetryConfig
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.opentelemetry.api.OpenTelemetry

/**
 * Provides the application's [OpenTelemetry] and registers its [OpenTelemetryService] into the app-service set, so
 * the last telemetry is exported, and the SDK shut down, when the application stops. Its [TelemetryConfig] is decoded
 * by [telemetryConfig], from the `telemetry.sdk` config section.
 *
 * It declares two sets, each of which may be empty:
 * - `Set<OpenTelemetryCustomizer>`: how other modules, such as `boot-telemetry-otel-prometheus-metro`, plug into the
 *   SDK. Contribute your own for what [TelemetryConfig] doesn't cover:
 *   ```
 *   @Provides @IntoSet fun noHealthTraces(): OpenTelemetryCustomizer = excludeUrlPathsFromTraces(setOf("/health"))
 *   ```
 * - `Set<MetricsSnapshotProvider>`: the scrape endpoints of pulled metrics backends, for an HTTP integration to serve.
 */
@ContributesTo(BootScope::class)
interface OpenTelemetryBindings {
  @Multibinds(allowEmpty = true) val openTelemetryCustomizers: Set<OpenTelemetryCustomizer>
  @Multibinds(allowEmpty = true) val metricsSnapshotProviders: Set<MetricsSnapshotProvider>

  companion object {
    @Provides @SingleIn(BootScope::class)
    fun telemetryConfig(binder: ConfigBinder): TelemetryConfig = decodeTelemetryConfig(binder)

    @Provides @SingleIn(BootScope::class)
    fun openTelemetryService(config: TelemetryConfig, customizers: Set<OpenTelemetryCustomizer>): OpenTelemetryService =
      OpenTelemetryService(config, customizers)

    @Provides fun openTelemetry(service: OpenTelemetryService): OpenTelemetry = service.openTelemetry

    @Provides @IntoSet fun openTelemetryAppService(service: OpenTelemetryService): AppService = service
  }
}
