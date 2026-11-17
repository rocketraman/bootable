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
 * Connects the application's telemetry to SigNoz: adds a [SigNozTelemetry], configured from the `telemetry.signoz`
 * config section, to the customizers of `OpenTelemetryBindings` (from boot-telemetry-otel-metro).
 */
@ContributesTo(BootScope::class)
@BindingContainer
object SigNozTelemetryBindings {
  @Provides @SingleIn(BootScope::class)
  fun sigNozTelemetryConfig(binder: ConfigBinder): SigNozTelemetryConfig = binder.config("telemetry.signoz")

  @Provides @SingleIn(BootScope::class)
  fun sigNozTelemetry(config: SigNozTelemetryConfig): SigNozTelemetry = SigNozTelemetry(config)

  @Provides @IntoSet fun sigNozCustomizer(sigNoz: SigNozTelemetry): OpenTelemetryCustomizer = sigNoz
}
