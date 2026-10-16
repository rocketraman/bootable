package com.github.rocketraman.bootable.db.jdbc

/**
 * How to connect to a database. [JdbcDataSourceService] builds its pool from this, using the [ConnectionPoolFactory]
 * of the chosen pool module.
 *
 * Use [JdbcGenericConfig] to configure any JDBC database by URL, or a driver module's typed config (for example
 * `SqliteConfig` from `boot-db-jdbc-sqlite`), which derives the URL and driver properties from database-specific
 * settings and brings the driver in as a dependency.
 */
interface JdbcConfig {
  val jdbcUrl: String

  /** The driver class. Usually unnecessary: JDBC 4 drivers register themselves and are found from [jdbcUrl]. */
  val driverClassName: String?

  val username: String?

  /**
   * The password. Hoplite's default secrets policy treats any field whose name contains "pass" as a secret, so
   * this is masked in the config report.
   */
  val password: String?

  /** Driver-specific connection properties, passed to the driver on every new connection. */
  val dataSourceProperties: Map<String, String>

  /**
   * The most connections the database copes well with, as stated by the driver module: e.g. 1 for SQLite, which
   * allows only one writer at a time. `null` means the database states no limit.
   *
   * A pool uses it as its size when its own config doesn't set one. A configured size still wins, but a pool should
   * warn when it exceeds the hint, since that is likely a mistake.
   */
  val maxConnectionsHint: Int? get() = null

  /**
   * Prepares what the database needs before the first connection is opened, such as creating the directory of a
   * SQLite database file. [JdbcDataSourceService] calls it when the application starts, just before it opens and
   * validates its first connection. Does nothing by default.
   */
  fun prepare() {}
}
