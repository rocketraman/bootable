package com.github.rocketraman.bootable.test

import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

/**
 * Adds [telemetry] to the customizers of `OpenTelemetryBindings` (from boot-telemetry-otel-metro), so a test collects
 * the spans and metrics recorded through it. Include it in a test graph through its factory, and keep [telemetry] to
 * assert on:
 *
 * ```
 * @DependencyGraph(TestScope::class, additionalScopes = [BootScope::class])
 * interface TelemetryGraph : BootableGraph {
 *   val openTelemetry: OpenTelemetry
 *
 *   @DependencyGraph.Factory
 *   fun interface Factory {
 *     fun create(@Includes telemetry: InMemoryTelemetryBindings): TelemetryGraph
 *   }
 * }
 * ```
 */
@BindingContainer
class InMemoryTelemetryBindings(val telemetry: InMemoryTelemetry = InMemoryTelemetry()) {
  @Provides @IntoSet fun inMemoryTelemetryCustomizer(): OpenTelemetryCustomizer = telemetry
}
