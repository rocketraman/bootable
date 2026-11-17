package com.github.rocketraman.bootable.db.jdbc

import org.kodein.di.DI
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that traces each JDBC statement through the application's OpenTelemetry, by adding [jdbcTracing] to
 * the [DataSourceWrapper] set of `jdbcDataSourceModule` (from boot-db-jdbc-kodein). Import it after
 * `jdbcDataSourceModule`, which opens that set, alongside `openTelemetryModule` (from boot-telemetry-otel-kodein),
 * which provides the `OpenTelemetry`.
 */
val jdbcTracingModule by DI.Module {
  inBindSet<DataSourceWrapper> { add { singleton { jdbcTracing(instance()) } } }
}
