package com.github.rocketraman.bootable.db.jdbc.koin

import com.github.rocketraman.bootable.db.jdbc.DataSourceWrapper
import com.github.rocketraman.bootable.db.jdbc.jdbcTracing
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetryBuilder
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that traces each JDBC statement through the application's OpenTelemetry, by adding [jdbcTracing] to
 * the [DataSourceWrapper]s of `jdbcDataSourceModule()` (from boot-db-jdbc-koin). Include it alongside
 * `jdbcDataSourceModule()`, and `openTelemetryModule()` (from boot-telemetry-otel-koin), which provides the
 * `OpenTelemetry`.
 *
 * [configure] changes the instrumentation's defaults, as [jdbcTracing]'s does, e.g.
 * `jdbcTracingModule { setDataSourceInstrumenterEnabled(true) }` for a span for each connection taken from the pool.
 */
fun jdbcTracingModule(configure: JdbcTelemetryBuilder.() -> Unit = {}): Module = module {
  single<DataSourceWrapper>(named("jdbcTracing")) { jdbcTracing(get(), configure) }
}
