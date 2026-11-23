package com.github.rocketraman.bootable.db.jdbc

import javax.sql.DataSource

/**
 * Wraps the pool's [DataSource] before [JdbcDataSourceService] hands it out, so that everything using the database's
 * connections goes through the wrapper, such as OpenTelemetry's JDBC tracing from `boot-db-jdbc-otel`.
 *
 * [wrap] runs once, when the service is constructed, and must not open a connection. The pool stays the service's to
 * close: a wrapper that holds resources of its own must release them itself.
 *
 * ```
 * JdbcDataSourceService(sqliteConfig, HikariConnectionPoolFactory(), jdbcTracing(openTelemetry))
 * ```
 */
fun interface DataSourceWrapper {
  fun wrap(dataSource: DataSource): DataSource

  companion object {
    /** Leaves the [DataSource] as it is. */
    val None: DataSourceWrapper = DataSourceWrapper { it }

    /**
     * Applies [wrappers] in their iteration order, so the last one is the outermost, and sees each call first. A
     * DI set, such as Metro's, or Koin's `getAll`, has no defined order, so wrappers taken from one should not depend on
     * one another's.
     */
    fun of(wrappers: Iterable<DataSourceWrapper>): DataSourceWrapper =
      DataSourceWrapper { dataSource -> wrappers.fold(dataSource) { wrapped, wrapper -> wrapper.wrap(wrapped) } }
  }
}
