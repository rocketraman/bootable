package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.boot.BootScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import io.opentelemetry.api.OpenTelemetry

/**
 * Traces each run of the application's periodic services through its OpenTelemetry (from boot-telemetry-otel-metro),
 * by contributing [periodicTracing] to the [PeriodicRunInterceptor] set of `PeriodicBindings` (from
 * boot-periodic-metro).
 */
@ContributesTo(BootScope::class)
interface PeriodicTracingBindings {
  companion object {
    @Provides @IntoSet
    fun periodicTracingInterceptor(openTelemetry: OpenTelemetry): PeriodicRunInterceptor = periodicTracing(openTelemetry)
  }
}
