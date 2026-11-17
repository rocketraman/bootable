package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.BootScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import io.opentelemetry.api.OpenTelemetry

/**
 * Records HikariCP's pool metrics through the application's OpenTelemetry (from boot-telemetry-otel-metro), by
 * contributing [hikariPoolMetrics] to the [HikariConfigCustomizer] set of `HikariPoolBindings` (from
 * boot-db-pool-hikari-metro).
 */
@ContributesTo(BootScope::class)
interface HikariPoolMetricsBindings {
  companion object {
    @Provides @IntoSet
    fun hikariPoolMetricsCustomizer(openTelemetry: OpenTelemetry): HikariConfigCustomizer =
      hikariPoolMetrics(openTelemetry)
  }
}
