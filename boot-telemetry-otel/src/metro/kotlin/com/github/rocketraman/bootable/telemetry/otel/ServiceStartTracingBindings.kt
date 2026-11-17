package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.LifecycleController
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import io.opentelemetry.api.OpenTelemetry

/**
 * Traces the application's start as one trace, with a span for each service's start, by contributing
 * [ServiceStartTracing] to the [LifecycleController] set of `BootableGraph` (from boot-metro), over the `OpenTelemetry`
 * of [OpenTelemetryBindings].
 *
 * Deliberately not contributed, so that depending on boot-telemetry-otel-metro doesn't change how the application
 * starts. Name it in your graph:
 *
 * ```
 * @DependencyGraph(AppScope::class, additionalScopes = [BootScope::class], bindingContainers = [ServiceStartTracingBindings::class])
 * ```
 */
@BindingContainer
object ServiceStartTracingBindings {
  @Provides @IntoSet
  fun serviceStartTracing(openTelemetry: OpenTelemetry): LifecycleController = ServiceStartTracing(openTelemetry)
}
