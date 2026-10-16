package com.github.rocketraman.bootable.db.jdbc

/**
 * How to connect to a database, and how to size the connection pool. [JdbcDataSourceService] builds its pool
 * from this.
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

  val pool: PoolConfig

  /**
   * The pool size used when [PoolConfig.maximumPoolSize] is not set. A driver module sets this when the
   * database needs a different default, e.g. SQLite allows only one writer. `null` means Hikari's default.
   */
  val defaultMaximumPoolSize: Int? get() = null
}
