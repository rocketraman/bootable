@file:JvmName("HikariPoolMetricsModuleInara")

package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryApiModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that records HikariCP's pool metrics through the application's OpenTelemetry, by adding
 * [hikariPoolMetrics] to the customizers of the [HikariPoolModule] it requires.
 */
@DIModule
interface HikariPoolMetricsModule : @DIModule.Required HikariPoolModule, @DIModule.Required OpenTelemetryApiModule {
  @HikariConfigCustomizers val hikariPoolMetrics: HikariConfigCustomizer
}

/** A [HikariPoolMetricsModule]. */
fun hikariPoolMetricsModule(): HikariPoolMetricsModulePart = HikariPoolMetricsModulePart(
  // qualified: the hikariPoolMetrics member shadows the top-level function
  hikariPoolMetrics = Binding.single { com.github.rocketraman.bootable.db.jdbc.hikariPoolMetrics(openTelemetry) },
)
