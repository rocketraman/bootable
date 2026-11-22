@file:JvmName("OpenTelemetryApiModuleInara")

package com.github.rocketraman.bootable.telemetry.otel

import io.opentelemetry.api.OpenTelemetry
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that provides the application's [OpenTelemetry], for the modules that trace or measure through it.
 * Compose `openTelemetryModule()` from boot-telemetry-otel, which provides it with the SDK, or, for an application
 * without telemetry, the [NoopOpenTelemetryModule].
 */
@DIModule
interface OpenTelemetryApiModule {
  val openTelemetry: OpenTelemetry
}

/** An [OpenTelemetryApiModule] with no telemetry: its [OpenTelemetry] records nothing. */
val NoopOpenTelemetryModule: OpenTelemetryApiModule =
  OpenTelemetryApiModule(openTelemetry = Binding.value(OpenTelemetry.noop()))
