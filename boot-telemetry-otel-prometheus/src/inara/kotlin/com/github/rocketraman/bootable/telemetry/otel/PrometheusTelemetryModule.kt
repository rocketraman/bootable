@file:JvmName("PrometheusTelemetryModuleInara")

package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.config.config
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that exposes the application's metrics for Prometheus to scrape: it adds a [PrometheusTelemetry], configured from the `telemetry.prometheus` config section, to the
 * customizers of the [OpenTelemetryModule] it requires, and to the metrics snapshot providers an HTTP integration serves.
 */
@DIModule
interface PrometheusTelemetryModule : @DIModule.Required OpenTelemetryModule {
  val prometheusTelemetryConfig: PrometheusTelemetryConfig
  @OpenTelemetryCustomizers @MetricsSnapshotProviders val prometheusTelemetry: PrometheusTelemetry
}

/** A [PrometheusTelemetryModule]. */
fun prometheusTelemetryModule(): PrometheusTelemetryModulePart = PrometheusTelemetryModulePart(
  prometheusTelemetryConfig = config("telemetry.prometheus"),
  prometheusTelemetry = Binding.single { PrometheusTelemetry(prometheusTelemetryConfig) },
)
