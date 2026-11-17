package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.BootScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import io.opentelemetry.api.OpenTelemetry

/**
 * Traces each JDBC statement through the application's OpenTelemetry (from boot-telemetry-otel-metro), by
 * contributing [jdbcTracing] to the [DataSourceWrapper] set of `JdbcDataSourceBindings` (from boot-db-jdbc-metro).
 */
@ContributesTo(BootScope::class)
interface JdbcTracingBindings {
  companion object {
    @Provides @IntoSet
    fun jdbcTracingWrapper(openTelemetry: OpenTelemetry): DataSourceWrapper = jdbcTracing(openTelemetry)
  }
}
