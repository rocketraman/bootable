@file:JvmName("KtorTelemetryModuleInara")

package com.github.rocketraman.bootable.server.http.ktor

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
 * Install it in a server with `installTelemetry(ktorTelemetry)`. For the scrape's listener of its own, add the
 * listener on your engine to the application's services, as for `KtorHealth`.
 *
 * To adjust the Ktor instrumentation beyond [KtorTelemetryConfig], mark a member [KtorServerTelemetryCustomizers].
 */
@DIModule(sets = [KtorServerTelemetryCustomizers::class])
interface KtorTelemetryModule : @DIModule.Required OpenTelemetryModule {
  val ktorTelemetryConfig: KtorTelemetryConfig
  @OpenTelemetryCustomizers val excludeScrapeFromTraces: OpenTelemetryCustomizer
  @OpenTelemetryCustomizers val excludeHealthFromTraces: OpenTelemetryCustomizer
  val ktorTelemetry: KtorTelemetry
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
)
