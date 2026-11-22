package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.AppService
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
 * also keeps scrapes of the application's server out of the traces, and the health probes of `KtorHealth`, at the
 * paths of the `health.ktor` config section.
 *
 * Install it in a server with `installTelemetry(telemetry)`.
 *
 * It also contributes the scrape's listener of its own to the app services, which does nothing unless
 * `telemetry.ktor.hostPort` is set, and then runs on the graph's [KtorServerEngine], such as
 * `KtorServerCioEngineBindings`'s, from boot-server-http-ktor-cio-metro.
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

    @Provides @IntoSet
    fun excludeHealthFromTraces(binder: ConfigBinder): OpenTelemetryCustomizer =
      ktorHealthConfig(binder).excludeHealthFromTraces()

    @Provides @SingleIn(BootScope::class)
    fun ktorTelemetry(
      openTelemetry: OpenTelemetry,
      snapshotProviders: Set<MetricsSnapshotProvider>,
      config: KtorTelemetryConfig,
      customizers: Set<KtorServerTelemetryCustomizer>,
    ): KtorTelemetry =
      KtorTelemetry(openTelemetry, snapshotProviders, config) { builder -> customizers.forEach { it.customize(builder) } }

    @Provides @IntoSet @SingleIn(BootScope::class)
    fun metricsListener(telemetry: KtorTelemetry, engine: KtorServerEngine = NoKtorServerEngine): AppService =
      telemetry.listener(engine.takeUnless { it === NoKtorServerEngine })
  }
}

/**
 * In place of the graph's [KtorServerEngine], when it has none: Metro takes a default value for a binding it doesn't
 * have, but a nullable parameter would be a binding of its own, which the engine's isn't.
 */
private object NoKtorServerEngine : KtorServerEngine {
  override val factory get() = error("no KtorServerEngine")
}
