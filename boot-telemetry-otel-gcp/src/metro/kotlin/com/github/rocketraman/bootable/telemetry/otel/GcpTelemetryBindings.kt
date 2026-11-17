package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Connects the application's telemetry to Google Cloud: adds a [GcpTelemetry], configured from the `telemetry.gcp`
 * config section, to the customizers of `OpenTelemetryBindings` (from boot-telemetry-otel-metro).
 */
@ContributesTo(BootScope::class)
@BindingContainer
object GcpTelemetryBindings {
  @Provides @SingleIn(BootScope::class)
  fun gcpTelemetryConfig(binder: ConfigBinder): GcpTelemetryConfig = binder.config("telemetry.gcp")

  @Provides @SingleIn(BootScope::class)
  fun gcpTelemetry(config: GcpTelemetryConfig): GcpTelemetry = GcpTelemetry(config)

  @Provides @IntoSet fun gcpCustomizer(gcp: GcpTelemetry): OpenTelemetryCustomizer = gcp
}
