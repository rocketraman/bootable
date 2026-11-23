package com.github.rocketraman.bootable.db.jdbc

import org.kodein.di.DI
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that records HikariCP's pool metrics through the application's OpenTelemetry, by adding
 * [hikariPoolMetrics] to the [HikariConfigCustomizer] set of `hikariPoolModule` (from boot-db-pool-hikari-kodein).
 * Import it after `hikariPoolModule`, which opens that set, alongside `openTelemetryModule` (from
 * boot-telemetry-otel-kodein), which provides the `OpenTelemetry`.
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val hikariPoolMetricsModule by DI.Module {
  inBindSet<HikariConfigCustomizer> { add { singleton { hikariPoolMetrics(instance()) } } }
}
