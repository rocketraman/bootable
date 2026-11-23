package com.github.rocketraman.bootable.db.jdbc

import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetryBuilder
import org.kodein.di.DI
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that traces each JDBC statement through the application's OpenTelemetry, by adding [jdbcTracing] to
 * the [DataSourceWrapper] set of `jdbcDataSourceModule` (from boot-db-jdbc-kodein). Import it after
 * `jdbcDataSourceModule`, which opens that set, alongside `openTelemetryModule` (from boot-telemetry-otel-kodein),
 * which provides the `OpenTelemetry`.
 *
 * [configure] changes the instrumentation's defaults, as [jdbcTracing]'s does, e.g.
 * `import(jdbcTracingModule { setDataSourceInstrumenterEnabled(true) })` for a span for each connection taken from the
 * pool.
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
fun jdbcTracingModule(configure: JdbcTelemetryBuilder.() -> Unit = {}): DI.Module = DI.Module("jdbcTracingModule") {
  inBindSet<DataSourceWrapper> { add { singleton { jdbcTracing(instance(), configure) } } }
}
