package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.telemetry.otel.MetricsSnapshotProvider
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.opentelemetry.api.OpenTelemetry

/**
 * Provides [KtorTelemetry], over the OpenTelemetry and metrics snapshot providers of `OpenTelemetryBindings` (from
 * boot-telemetry-otel-metro), with its [KtorTelemetryConfig] decoded from the `telemetry.ktor` config section. It
 * also keeps scrapes of the application's server out of the traces.
 *
 * Install it in a server with `installTelemetry(telemetry)`. For the scrape's listener of its own, add the listener
 * on your engine to the app services:
 *
 * ```
 * @Provides @IntoSet fun metricsListener(telemetry: KtorTelemetry): AppService = telemetry.listener(CIO)
 * ```
 *
 * To adjust the Ktor instrumentation beyond [KtorTelemetryConfig], contribute a [KtorServerTelemetryCustomizer].
 */
@ContributesTo(BootScope::class)
interface KtorTelemetryBindings {
  @Multibinds(allowEmpty = true) val ktorServerTelemetryCustomizers: Set<KtorServerTelemetryCustomizer>

  companion object {
    @Provides @SingleIn(BootScope::class)
    fun ktorTelemetryConfig(binder: ConfigBinder): KtorTelemetryConfig = binder.config("telemetry.ktor")

    @Provides @IntoSet
    fun excludeScrapeFromTraces(config: KtorTelemetryConfig): OpenTelemetryCustomizer = config.excludeScrapeFromTraces()

    @Provides @SingleIn(BootScope::class)
    fun ktorTelemetry(
      openTelemetry: OpenTelemetry,
      snapshotProviders: Set<MetricsSnapshotProvider>,
      config: KtorTelemetryConfig,
      customizers: Set<KtorServerTelemetryCustomizer>,
    ): KtorTelemetry =
      KtorTelemetry(openTelemetry, snapshotProviders, config) { builder -> customizers.forEach { it.customize(builder) } }
  }
}
