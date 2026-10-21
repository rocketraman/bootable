package com.github.rocketraman.bootable.telemetry.otel

import io.micrometer.core.instrument.MeterRegistry
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance

/**
 * A Kodein module that binds a Micrometer [MeterRegistry] recording into the application's OpenTelemetry (from
 * `openTelemetryModule`), for libraries that report only to Micrometer: see [openTelemetryMeterRegistry].
 */
val micrometerBridgeModule by DI.Module {
  bindSingleton<MeterRegistry> { openTelemetryMeterRegistry(instance()) }
}
