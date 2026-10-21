package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Exposes the application's metrics for Prometheus to scrape: adds a [PrometheusTelemetry], configured from the
 * `telemetry.prometheus` config section, to the customizers of `OpenTelemetryBindings` (from
 * boot-telemetry-otel-metro), which registers its metric reader with the SDK, and to the metrics snapshot providers an HTTP integration
 * serves.
 */
@ContributesTo(BootScope::class)
@BindingContainer
object PrometheusTelemetryBindings {
  @Provides @SingleIn(BootScope::class)
  fun prometheusTelemetryConfig(binder: ConfigBinder): PrometheusTelemetryConfig = binder.config("telemetry.prometheus")

  @Provides @SingleIn(BootScope::class)
  fun prometheusTelemetry(config: PrometheusTelemetryConfig): PrometheusTelemetry = PrometheusTelemetry(config)

  @Provides @IntoSet fun prometheusCustomizer(prometheus: PrometheusTelemetry): OpenTelemetryCustomizer = prometheus

  @Provides @IntoSet fun prometheusSnapshotProvider(prometheus: PrometheusTelemetry): MetricsSnapshotProvider = prometheus
}
