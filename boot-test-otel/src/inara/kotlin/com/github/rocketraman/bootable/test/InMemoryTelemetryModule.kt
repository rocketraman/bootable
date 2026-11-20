@file:JvmName("InMemoryTelemetryModuleInara")

package com.github.rocketraman.bootable.test

import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizers
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that adds an [InMemoryTelemetry] to the customizers of the [OpenTelemetryModule] it requires, so a
 * test collects the spans and metrics recorded through it.
 */
@DIModule
interface InMemoryTelemetryModule : @DIModule.Required OpenTelemetryModule {
  @OpenTelemetryCustomizers val inMemoryTelemetry: InMemoryTelemetry
}

/** An [InMemoryTelemetryModule] with [telemetry]: keep it to assert on, or read it from the module. */
fun inMemoryTelemetryModule(telemetry: InMemoryTelemetry = InMemoryTelemetry()): InMemoryTelemetryModulePart =
  InMemoryTelemetryModulePart(inMemoryTelemetry = Binding.value(telemetry))
