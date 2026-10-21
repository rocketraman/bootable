package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import org.kodein.di.DI
import org.kodein.di.bindSet
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that provides [KtorTelemetry], over the OpenTelemetry and metrics snapshot providers of
 * `openTelemetryModule` (from boot-telemetry-otel-kodein), with its [KtorTelemetryConfig] decoded from the
 * `telemetry.ktor` config section. It also keeps scrapes of the application's server out of the traces. Import it
 * after `openTelemetryModule`. Requires a `ConfigBinder` binding (e.g. from boot-config-hoplite's
 * `hopliteConfigModule`).
 *
 * Install it in a server with `installTelemetry(instance<KtorTelemetry>())`. For the scrape's listener of its own,
 * add the listener on your engine to the app services:
 *
 * ```
 * bindAppService { singleton { instance<KtorTelemetry>().listener(CIO) } }
 * ```
 *
 * To adjust the Ktor instrumentation beyond [KtorTelemetryConfig], add a [KtorServerTelemetryCustomizer] to the set
 * this module opens.
 */
val ktorTelemetryModule by DI.Module {
  bindSet<KtorServerTelemetryCustomizer>()
  bindSingleton<KtorTelemetryConfig> { instance<ConfigBinder>().config("telemetry.ktor") }
  inBindSet<OpenTelemetryCustomizer> { add { singleton { instance<KtorTelemetryConfig>().excludeScrapeFromTraces() } } }
  bindSingleton {
    val customizers = instance<Set<KtorServerTelemetryCustomizer>>()
    KtorTelemetry(instance(), instance(), instance()) { builder -> customizers.forEach { it.customize(builder) } }
  }
}
