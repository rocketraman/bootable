package com.github.rocketraman.bootable.server.http.ktor.koin

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.config.koin.config
import com.github.rocketraman.bootable.server.http.ktor.KtorServerEngine
import com.github.rocketraman.bootable.server.http.ktor.KtorServerTelemetryCustomizer
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetryConfig
import com.github.rocketraman.bootable.server.http.ktor.excludeHealthFromTraces
import com.github.rocketraman.bootable.server.http.ktor.ktorHealthConfig
import com.github.rocketraman.bootable.telemetry.otel.MetricsSnapshotProvider
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that provides [KtorTelemetry], over the OpenTelemetry and metrics snapshot providers of
 * `openTelemetryModule()` (from boot-telemetry-otel-koin), with its [KtorTelemetryConfig] decoded from the
 * `telemetry.ktor` config section. It also keeps scrapes of the application's server out of the traces, and the
 * health probes of `KtorHealth`, at the paths of the `health.ktor` config section. Requires a `ConfigBinder`
 * definition (e.g. from boot-config-hoplite's `hopliteConfigModule()`).
 *
 * Install it in a server with `installTelemetry(get<KtorTelemetry>())`.
 *
 * It also adds the scrape's listener of its own to the app services, which does nothing unless
 * `telemetry.ktor.hostPort` is set, and then runs on the application's [KtorServerEngine], such as
 * `ktorServerCioEngineModule()`'s, from boot-server-http-ktor-cio-koin.
 *
 * To adjust the Ktor instrumentation beyond [KtorTelemetryConfig], define a [KtorServerTelemetryCustomizer], with a
 * qualifier of its own.
 */
fun ktorTelemetryModule(): Module = module {
  config<KtorTelemetryConfig>("telemetry.ktor")
  single<OpenTelemetryCustomizer>(named("ktorScrapeExclusion")) { get<KtorTelemetryConfig>().excludeScrapeFromTraces() }
  single<OpenTelemetryCustomizer>(named("ktorHealthExclusion")) { ktorHealthConfig(get()).excludeHealthFromTraces() }
  single {
    val customizers = getAll<KtorServerTelemetryCustomizer>()
    KtorTelemetry(get(), getAll<MetricsSnapshotProvider>().toSet(), get()) { builder ->
      customizers.forEach { it.customize(builder) }
    }
  }
  single<AppService>(named("ktorTelemetryListener")) { get<KtorTelemetry>().listener(getOrNull<KtorServerEngine>()) }
}
