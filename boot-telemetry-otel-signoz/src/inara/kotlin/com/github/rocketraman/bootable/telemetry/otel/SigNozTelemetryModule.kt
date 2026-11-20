@file:JvmName("SigNozTelemetryModuleInara")

package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.config.config
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that connects the application's telemetry to SigNoz: it adds a [SigNozTelemetry], configured from the `telemetry.signoz` config section, to the
 * customizers of the [OpenTelemetryModule] it requires.
 */
@DIModule
interface SigNozTelemetryModule : @DIModule.Required OpenTelemetryModule {
  val sigNozTelemetryConfig: SigNozTelemetryConfig
  @OpenTelemetryCustomizers val sigNozTelemetry: SigNozTelemetry
}

/** A [SigNozTelemetryModule]. */
fun sigNozTelemetryModule(): SigNozTelemetryModulePart = SigNozTelemetryModulePart(
  sigNozTelemetryConfig = config("telemetry.signoz"),
  sigNozTelemetry = Binding.single { SigNozTelemetry(sigNozTelemetryConfig) },
)
