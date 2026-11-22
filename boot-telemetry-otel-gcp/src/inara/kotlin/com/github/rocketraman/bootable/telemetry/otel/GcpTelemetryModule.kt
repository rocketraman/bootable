@file:JvmName("GcpTelemetryModuleInara")

package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.config.config
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that connects the application's telemetry to Google Cloud: it adds a [GcpTelemetry], configured from the `telemetry.gcp` config section, to the
 * customizers of the [OpenTelemetryModule] it requires.
 */
@DIModule
interface GcpTelemetryModule : @DIModule.Required OpenTelemetryModule {
  val gcpTelemetryConfig: GcpTelemetryConfig
  @OpenTelemetryCustomizers val gcpTelemetry: GcpTelemetry
}

/** A [GcpTelemetryModule]. */
fun gcpTelemetryModule(): GcpTelemetryModulePart = GcpTelemetryModulePart(
  gcpTelemetryConfig = config("telemetry.gcp"),
  gcpTelemetry = Binding.single { GcpTelemetry(gcpTelemetryConfig) },
)
