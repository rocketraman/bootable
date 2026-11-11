@file:JvmName("KtorTelemetryModuleInara")

package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizers
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.setOfMetricsSnapshotProviders
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.inara.DISet

/** Marks a module member as one of the [KtorServerTelemetryCustomizer]s of the Ktor server instrumentation. */
@DISet(of = KtorServerTelemetryCustomizer::class)
annotation class KtorServerTelemetryCustomizers

/**
 * The Inara module that provides [KtorTelemetry], over the OpenTelemetry and metrics snapshot providers of the
 * [OpenTelemetryModule] it requires, with its [KtorTelemetryConfig] decoded from the `telemetry.ktor` config section.
 * It also keeps scrapes of the application's server out of the traces, and the health probes of `KtorHealth`, at the
 * paths of the `health.ktor` config section.
 *
 * Install it in a server with `installTelemetry(ktorTelemetry)`.
 *
 * It also adds the scrape's listener of its own to the app services, which does nothing unless
 * `telemetry.ktor.hostPort` is set, and then runs on the [KtorServerEngine] of the [KtorServerEngineModule] it
 * requires, such as `ktorServerCioEngineModule()`, from boot-server-http-ktor-cio-inara.
 *
 * To adjust the Ktor instrumentation beyond [KtorTelemetryConfig], mark a member [KtorServerTelemetryCustomizers].
 */
@DIModule(sets = [KtorServerTelemetryCustomizers::class])
interface KtorTelemetryModule : @DIModule.Required OpenTelemetryModule, @DIModule.Required KtorServerEngineModule {
  val ktorTelemetryConfig: KtorTelemetryConfig
  @OpenTelemetryCustomizers val excludeScrapeFromTraces: OpenTelemetryCustomizer
  @OpenTelemetryCustomizers val excludeHealthFromTraces: OpenTelemetryCustomizer
  val ktorTelemetry: KtorTelemetry
  @AppServices val ktorTelemetryListener: AppService
}

/** A [KtorTelemetryModule]. */
fun ktorTelemetryModule(): KtorTelemetryModulePart = KtorTelemetryModulePart(
  ktorTelemetryConfig = config("telemetry.ktor"),
  excludeScrapeFromTraces = Binding.single { ktorTelemetryConfig.excludeScrapeFromTraces() },
  excludeHealthFromTraces = Binding.single { ktorHealthConfig(configBinder).excludeHealthFromTraces() },
  ktorTelemetry = Binding.single {
    val customizers = setOfKtorServerTelemetryCustomizers()
    KtorTelemetry(openTelemetry, setOfMetricsSnapshotProviders(), ktorTelemetryConfig) { builder ->
      customizers.forEach { it.customize(builder) }
    }
  },
  ktorTelemetryListener = Binding.single { ktorTelemetry.listener(ktorServerEngine) },
)
