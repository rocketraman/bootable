package com.github.rocketraman.bootable.db.jdbc

import javax.sql.DataSource

/**
 * A connection pool, as seen by [JdbcDataSourceService]. Implemented by a pool module, such as
 * `boot-db-pool-hikari`.
 *
 * A pool must not open connections when it is created, only on first use: [JdbcDataSourceService] hands out
 * [dataSource] immediately, but opens the first connection when the application starts, so that an unreachable
 * database stops the application at a defined point in its lifecycle.
 */
interface ConnectionPool {
  /** The pooled connections. */
  val dataSource: DataSource

  /** Closes the pool and all its connections. */
  fun close()
}

/**
 * Creates the [ConnectionPool] for a database. This is how a pool implementation plugs into `boot-db-jdbc`: choose
 * a driver module for the [JdbcConfig], and a pool module for the [ConnectionPoolFactory].
 *
 * The factory owns its own pool settings, typically a config class decoded from the `db.pool` config section. When
 * those settings don't size the pool, it should use the database's [JdbcConfig.maxConnectionsHint]; when they size it
 * above the hint, it should keep the configured size but warn.
 */
fun interface ConnectionPoolFactory {
  fun create(config: JdbcConfig): ConnectionPool
}
