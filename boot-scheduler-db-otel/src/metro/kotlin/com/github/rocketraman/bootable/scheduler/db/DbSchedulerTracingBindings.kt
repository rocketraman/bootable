package com.github.rocketraman.bootable.scheduler.db

import com.github.rocketraman.bootable.boot.BootScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import io.opentelemetry.api.OpenTelemetry

/**
 * Traces each execution of the application's db-scheduler tasks through its OpenTelemetry (from
 * boot-telemetry-otel-metro), by contributing [dbSchedulerTracing] to the [DbSchedulerCustomizer] set of
 * `DbSchedulerBindings` (from boot-scheduler-db-metro).
 */
@ContributesTo(BootScope::class)
interface DbSchedulerTracingBindings {
  companion object {
    @Provides @IntoSet
    fun dbSchedulerTracingCustomizer(openTelemetry: OpenTelemetry): DbSchedulerCustomizer =
      dbSchedulerTracing(openTelemetry)
  }
}
