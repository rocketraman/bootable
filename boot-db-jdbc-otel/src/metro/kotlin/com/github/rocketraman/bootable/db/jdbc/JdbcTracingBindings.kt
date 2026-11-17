package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.BootScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetryBuilder

/**
 * Traces each JDBC statement through the application's OpenTelemetry (from boot-telemetry-otel-metro), by
 * contributing [jdbcTracing] to the [DataSourceWrapper] set of `JdbcDataSourceBindings` (from boot-db-jdbc-metro).
 *
 * A [JdbcTelemetryConfigurer] binding, if the graph has one, changes the instrumentation's defaults.
 */
@ContributesTo(BootScope::class)
interface JdbcTracingBindings {
  companion object {
    @Provides @IntoSet
    fun jdbcTracingWrapper(
      openTelemetry: OpenTelemetry,
      configurer: JdbcTelemetryConfigurer = JdbcTelemetryConfigurer {},
    ): DataSourceWrapper = jdbcTracing(openTelemetry) { with(configurer) { configure() } }
  }
}

/**
 * Changes the defaults of the JDBC instrumentation that [JdbcTracingBindings] builds, as [jdbcTracing]'s `configure`
 * does. Provide one in the graph, e.g. for a span for each connection taken from the pool:
 *
 * ```
 * @Provides fun jdbcTelemetryConfigurer(): JdbcTelemetryConfigurer = JdbcTelemetryConfigurer { setDataSourceInstrumenterEnabled(true) }
 * ```
 */
fun interface JdbcTelemetryConfigurer {
  fun JdbcTelemetryBuilder.configure()
}
