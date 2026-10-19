package com.github.rocketraman.bootable.db.jdbc

import kotlin.time.Duration

/**
 * A strongly typed [JdbcConfig] for a PostgreSQL database, connected to with the
 * [PostgreSQL JDBC driver](https://jdbc.postgresql.org/), which this module brings in. It brings Flyway's PostgreSQL
 * database module too, for `boot-db-flyway`.
 *
 * Decoded from the `db.jdbc` config section:
 *
 * ```
 * db {
 *   jdbc {
 *     host = db.example.com
 *     database = app
 *     username = app
 *     password = ${?DB_PASSWORD}
 *     sslMode = VERIFY_FULL
 *   }
 * }
 * ```
 *
 * The settings other than the address and credentials are passed to the driver as connection properties. Those
 * left `null` keep the driver's default. For a server address this can't express, such as several hosts for
 * failover, use [JdbcGenericConfig] with a JDBC URL instead.
 *
 * @property host The server's host name or IP address.
 * @property port The server's port.
 * @property database The database to connect to.
 * @property currentSchema The schema search path for the connection, e.g. `app` or `app,public`.
 * @property applicationName The name the connection reports to the server, shown in `pg_stat_activity`.
 * @property sslMode Whether to use SSL, and how much of the server's certificate to verify.
 * @property connectTimeout How long to wait to open a connection to the server. Whole seconds; the driver's
 *   default is 10 seconds.
 * @property socketTimeout How long to wait for a read from the server, which bounds every query; the driver waits
 *   forever by default. Whole seconds.
 * @property reWriteBatchedInserts Whether to rewrite a batch of `INSERT`s into multi-row `INSERT`s, which is much
 *   faster. The driver then reports the batch's update counts as `SUCCESS_NO_INFO`.
 */
data class PostgresqlConfig(
  val host: String = "localhost",
  val port: Int = 5432,
  val database: String,
  override val username: String? = null,
  override val password: String? = null,
  val currentSchema: String? = null,
  val applicationName: String? = null,
  val sslMode: PostgresqlSslMode? = null,
  val connectTimeout: Duration? = null,
  val socketTimeout: Duration? = null,
  val reWriteBatchedInserts: Boolean? = null,
) : JdbcConfig {
  override val jdbcUrl get() = "jdbc:postgresql://${urlHost(host)}:$port/$database"
  override val driverClassName get() = "org.postgresql.Driver"

  override val dataSourceProperties: Map<String, String>
    get() = buildMap {
      currentSchema?.let { put("currentSchema", it) }
      applicationName?.let { put("ApplicationName", it) }
      sslMode?.let { put("sslmode", it.value) }
      connectTimeout?.let { put("connectTimeout", it.inWholeSeconds.toString()) }
      socketTimeout?.let { put("socketTimeout", it.inWholeSeconds.toString()) }
      reWriteBatchedInserts?.let { put("reWriteBatchedInserts", it.toString()) }
    }

  // keep the password out of logs and exception messages
  override fun toString() =
    "PostgresqlConfig(host=$host, port=$port, database=$database, username=$username, " +
      "password=${password?.let { "****" }}, currentSchema=$currentSchema, applicationName=$applicationName, " +
      "sslMode=$sslMode, connectTimeout=$connectTimeout, socketTimeout=$socketTimeout, " +
      "reWriteBatchedInserts=$reWriteBatchedInserts)"
}

/**
 * PostgreSQL's [SSL modes](https://jdbc.postgresql.org/documentation/ssl/). Only [VERIFY_CA] and [VERIFY_FULL]
 * protect against a server impersonating the real one; [VERIFY_FULL] also checks the host name.
 */
enum class PostgresqlSslMode(val value: String) {
  DISABLE("disable"),
  ALLOW("allow"),
  PREFER("prefer"),
  REQUIRE("require"),
  VERIFY_CA("verify-ca"),
  VERIFY_FULL("verify-full"),
}

/** A host for a JDBC URL: an IPv6 address must be bracketed, so its colons aren't read as the port. */
private fun urlHost(host: String) = if (':' in host && !host.startsWith("[")) "[$host]" else host
