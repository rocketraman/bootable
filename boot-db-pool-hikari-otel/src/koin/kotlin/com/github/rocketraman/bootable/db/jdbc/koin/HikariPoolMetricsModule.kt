package com.github.rocketraman.bootable.db.jdbc.koin

import com.github.rocketraman.bootable.db.jdbc.HikariConfigCustomizer
import com.github.rocketraman.bootable.db.jdbc.hikariPoolMetrics
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that records HikariCP's pool metrics through the application's OpenTelemetry, by adding
 * [hikariPoolMetrics] to the [HikariConfigCustomizer]s of `hikariPoolModule()` (from boot-db-pool-hikari-koin). Include
 * it alongside `hikariPoolModule()`, and `openTelemetryModule()` (from boot-telemetry-otel-koin), which provides the
 * `OpenTelemetry`.
 */
fun hikariPoolMetricsModule(): Module = module {
  single<HikariConfigCustomizer>(named("hikariPoolMetrics")) { hikariPoolMetrics(get()) }
}
