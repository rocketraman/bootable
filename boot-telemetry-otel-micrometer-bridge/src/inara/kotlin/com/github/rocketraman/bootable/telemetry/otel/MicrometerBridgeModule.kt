@file:JvmName("MicrometerBridgeModuleInara")

package com.github.rocketraman.bootable.telemetry.otel

import io.micrometer.core.instrument.MeterRegistry
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that provides a Micrometer [MeterRegistry] recording into the OpenTelemetry of the
 * [OpenTelemetryApiModule] it requires, for libraries that report only to Micrometer: see [openTelemetryMeterRegistry].
 */
@DIModule
interface MicrometerBridgeModule : @DIModule.Required OpenTelemetryApiModule {
  val meterRegistry: MeterRegistry
}

/** A [MicrometerBridgeModule]. */
fun micrometerBridgeModule(): MicrometerBridgeModulePart =
  MicrometerBridgeModulePart(meterRegistry = Binding.single { openTelemetryMeterRegistry(openTelemetry) })
