package com.github.rocketraman.bootable.telemetry.otel.koin

import com.github.rocketraman.bootable.telemetry.otel.openTelemetryMeterRegistry
import io.micrometer.core.instrument.MeterRegistry
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * A Koin module that defines a Micrometer [MeterRegistry] recording into the application's OpenTelemetry (from
 * `openTelemetryModule()`), for libraries that report only to Micrometer: see [openTelemetryMeterRegistry].
 */
fun micrometerBridgeModule(): Module = module {
  single<MeterRegistry> { openTelemetryMeterRegistry(get()) }
}
