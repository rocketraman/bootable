package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.bindAppService
import com.github.rocketraman.bootable.config.bindConfig
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import org.kodein.di.DI
import org.kodein.di.bindSet
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.instanceOrNull
import org.kodein.di.singleton

/**
 * A Kodein module that provides [KtorTelemetry], over the OpenTelemetry and metrics snapshot providers of
 * `openTelemetryModule` (from boot-telemetry-otel-kodein), with its [KtorTelemetryConfig] decoded from the
 * `telemetry.ktor` config section. It also keeps scrapes of the application's server out of the traces, and the
 * health probes of `KtorHealth`, at the paths of the `health.ktor` config section. Import it
 * after `openTelemetryModule`. Requires a `ConfigBinder` binding (e.g. from boot-config-hoplite's
 * `hopliteConfigModule`).
 *
 * Install it in a server with `installTelemetry(instance<KtorTelemetry>())`.
 *
 * It also adds the scrape's listener of its own to the app services, which does nothing unless
 * `telemetry.ktor.hostPort` is set, and then runs on the application's [KtorServerEngine], such as
 * `ktorServerCioEngineModule`'s, from boot-server-http-ktor-cio-kodein.
 *
 * To adjust the Ktor instrumentation beyond [KtorTelemetryConfig], add a [KtorServerTelemetryCustomizer] to the set
 * this module opens.
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val ktorTelemetryModule by DI.Module {
  bindSet<KtorServerTelemetryCustomizer>()
  bindConfig<KtorTelemetryConfig>("telemetry.ktor")
  inBindSet<OpenTelemetryCustomizer> {
    add { singleton { instance<KtorTelemetryConfig>().excludeScrapeFromTraces() } }
    add { singleton { ktorHealthConfig(instance()).excludeHealthFromTraces() } }
  }
  bindSingleton {
    val customizers = instance<Set<KtorServerTelemetryCustomizer>>()
    KtorTelemetry(instance(), instance(), instance()) { builder -> customizers.forEach { it.customize(builder) } }
  }
  bindAppService { singleton { instance<KtorTelemetry>().listener(instanceOrNull<KtorServerEngine>()) } }
}
