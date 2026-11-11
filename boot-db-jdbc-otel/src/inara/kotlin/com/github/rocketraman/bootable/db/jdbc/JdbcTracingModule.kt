@file:JvmName("JdbcTracingModuleInara")

package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryApiModule
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetryBuilder
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that traces each JDBC statement through the application's OpenTelemetry, by adding [jdbcTracing] to
 * the [DataSourceWrapper]s of the [JdbcDataSourceModule] it requires.
 */
@DIModule
interface JdbcTracingModule : @DIModule.Required JdbcDataSourceModule, @DIModule.Required OpenTelemetryApiModule {
  @DataSourceWrappers val jdbcTracing: DataSourceWrapper
}

/**
 * A [JdbcTracingModule]. [configure] changes the instrumentation's defaults, as [jdbcTracing]'s does, e.g.
 * `jdbcTracingModule { setDataSourceInstrumenterEnabled(true) }` for a span for each connection taken from the pool.
 */
fun jdbcTracingModule(configure: JdbcTelemetryBuilder.() -> Unit = {}): JdbcTracingModulePart = JdbcTracingModulePart(
  // qualified: the jdbcTracing member shadows the top-level function
  jdbcTracing = Binding.single { com.github.rocketraman.bootable.db.jdbc.jdbcTracing(openTelemetry, configure) },
)
