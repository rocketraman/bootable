package com.github.rocketraman.bootable.db.jdbc

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetryBuilder

/**
 * A [DataSourceWrapper] that traces each JDBC statement the pool's `DataSource` runs, through [openTelemetry], with
 * OpenTelemetry's JDBC instrumentation. Each statement is a client span, named for its operation and table, such as
 * `SELECT entry`, a child of the span current on the thread that runs it.
 *
 * Give it to [JdbcDataSourceService]:
 *
 * ```
 * JdbcDataSourceService(sqliteConfig, HikariConnectionPoolFactory(), jdbcTracing(telemetry.openTelemetry))
 * ```
 *
 * The span's query text is sanitized, with its literals replaced by `?`, and its parameters are not captured. [configure]
 * changes the instrumentation's defaults, e.g. `setCaptureQueryParameters(true)`, which records each parameter's value,
 * so mind what that puts in your traces.
 *
 * The span's parent is OpenTelemetry's current context on the thread the statement runs on. Within a Ktor request
 * traced by `boot-server-http-ktor-otel`, or a `withSpan` from `boot-telemetry-otel`, that is the coroutine's span, on
 * whichever thread it runs, Exposed's `withTransaction` dispatcher included: the context is a coroutine context element,
 * which a coroutine's children inherit. A statement run outside any span starts a trace of its own: at startup, such as
 * a migration's, `ServiceStartTracing` from `boot-telemetry-otel` traces the application's start as one trace.
 */
fun jdbcTracing(
  openTelemetry: OpenTelemetry,
  configure: JdbcTelemetryBuilder.() -> Unit = {},
): DataSourceWrapper {
  val telemetry = JdbcTelemetry.builder(openTelemetry).apply(configure).build()
  return DataSourceWrapper(telemetry::wrap)
}
