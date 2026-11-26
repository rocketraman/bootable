@file:JvmName("OpenTelemetryModuleInara")

package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.LifecycleControllers
import com.github.rocketraman.bootable.clock.ClockModule
import com.github.rocketraman.bootable.config.ConfigModule
import io.opentelemetry.api.OpenTelemetry
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.inara.DISet

/** Marks a module member as one of the [OpenTelemetryCustomizer]s of the application's OpenTelemetry SDK. */
@DISet(of = OpenTelemetryCustomizer::class)
annotation class OpenTelemetryCustomizers

/** Marks a module member as one of the [MetricsSnapshotProvider]s, the scrape endpoints an HTTP integration serves. */
@DISet(of = MetricsSnapshotProvider::class)
annotation class MetricsSnapshotProviders

/**
 * The Inara module that provides the application's [OpenTelemetry], with the SDK, as an [OpenTelemetryApiModule]. Its
 * [TelemetryConfig] is decoded by [telemetryConfig], from the `telemetry.sdk` config section of the [ConfigModule] it
 * requires. Its [openTelemetryService] is one of the application's services, so the last telemetry is exported, and
 * the SDK shut down, when the application stops.
 *
 * Other modules, such as `prometheusTelemetryModule()` from `boot-telemetry-otel-prometheus`, plug into the SDK with
 * members marked [OpenTelemetryCustomizers], as the application can for what [TelemetryConfig] doesn't cover:
 *
 * ```
 * @OpenTelemetryCustomizers val noHealthTraces: OpenTelemetryCustomizer
 * ```
 *
 * The scrape endpoints of pulled metrics backends are its [MetricsSnapshotProviders], for an HTTP integration to serve.
 *
 * The SDK's clock is the clock of the [ClockModule] it requires. A test sets the time of the telemetry with a
 * `clockModule(testClock)`.
 */
@DIModule(sets = [OpenTelemetryCustomizers::class, MetricsSnapshotProviders::class])
interface OpenTelemetryModule :
  OpenTelemetryApiModule, @DIModule.Required ConfigModule, @DIModule.Required ClockModule {
  val telemetryConfig: TelemetryConfig
  @AppServices val openTelemetryService: OpenTelemetryService
}

/** An [OpenTelemetryModule]. */
fun openTelemetryModule(): OpenTelemetryModulePart = OpenTelemetryModulePart(
  // qualified: the telemetryConfig member shadows the top-level function
  telemetryConfig = Binding.single { com.github.rocketraman.bootable.telemetry.otel.telemetryConfig(configBinder) },
  openTelemetryService = Binding.single {
    OpenTelemetryService(telemetryConfig, setOfOpenTelemetryCustomizers(), clock)
  },
  openTelemetry = Binding.single { openTelemetryService.openTelemetry },
)

/**
 * The Inara module that traces the application's start as one trace, with a span for each service's start, by adding
 * [ServiceStartTracing] to the application's lifecycle controllers, through the OpenTelemetry of the
 * [OpenTelemetryApiModule] it requires.
 */
@DIModule
interface ServiceStartTracingModule : @DIModule.Required OpenTelemetryApiModule, @DIModule.Required ClockModule {
  @LifecycleControllers val serviceStartTracing: ServiceStartTracing
}

/** A [ServiceStartTracingModule], whose spans are timed by the clock of the [ClockModule] it requires. */
fun serviceStartTracingModule(): ServiceStartTracingModulePart =
  ServiceStartTracingModulePart(serviceStartTracing = Binding.single { ServiceStartTracing(openTelemetry, clock) })
