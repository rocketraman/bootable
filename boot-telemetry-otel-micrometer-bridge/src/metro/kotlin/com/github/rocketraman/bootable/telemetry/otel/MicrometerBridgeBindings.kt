package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.BootScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.api.OpenTelemetry

/**
 * Provides a Micrometer [MeterRegistry] recording into the application's OpenTelemetry (from
 * `OpenTelemetryBindings` in boot-telemetry-otel-metro), for libraries that report only to Micrometer: see
 * [openTelemetryMeterRegistry].
 */
@ContributesTo(BootScope::class)
@BindingContainer
object MicrometerBridgeBindings {
  @Provides @SingleIn(BootScope::class)
  fun meterRegistry(openTelemetry: OpenTelemetry): MeterRegistry = openTelemetryMeterRegistry(openTelemetry)
}
