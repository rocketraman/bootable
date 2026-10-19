package com.github.rocketraman.bootable.db.jdbc

import kotlin.time.Duration

/**
 * A strongly typed [JdbcConfig] for a MySQL database, connected to with
 * [MySQL Connector/J](https://dev.mysql.com/doc/connector-j/en/), which this module brings in. It brings Flyway's
 * MySQL database module too, for `boot-db-flyway`.
 *
 * Its defaults turn on Connector/J's client-side prepared statement cache, sized as
 * [HikariCP recommends](https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration): the driver
 * otherwise parses every statement again each time it is prepared.
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
 *     sslMode = VERIFY_IDENTITY
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
 * @property sslMode Whether to use SSL, and how much of the server's certificate to verify.
 * @property allowPublicKeyRetrieval Whether the driver may ask the server for its RSA public key, which the
 *   `caching_sha2_password` authentication needs on a connection without SSL. Off by default in the driver, since a
 *   server impersonating the real one could then read the password.
 * @property connectTimeout How long to wait to open a connection to the server; the driver waits forever by
 *   default.
 * @property socketTimeout How long to wait for a read from the server, which bounds every query; the driver waits
 *   forever by default.
 * @property rewriteBatchedStatements Whether to rewrite a batch of statements into fewer, multi-row statements,
 *   which is much faster.
 * @property cachePrepStmts Whether to cache prepared statements' parsed form on each connection.
 * @property prepStmtCacheSize How many prepared statements to cache on each connection.
 * @property prepStmtCacheSqlLimit The longest SQL statement, in characters, that is cached.
 */
data class MysqlConfig(
  val host: String = "localhost",
  val port: Int = 3306,
  val database: String,
  override val username: String? = null,
  override val password: String? = null,
  val sslMode: MysqlSslMode? = null,
  val allowPublicKeyRetrieval: Boolean? = null,
  val connectTimeout: Duration? = null,
  val socketTimeout: Duration? = null,
  val rewriteBatchedStatements: Boolean? = null,
  val cachePrepStmts: Boolean = true,
  val prepStmtCacheSize: Int = 250,
  val prepStmtCacheSqlLimit: Int = 2048,
) : JdbcConfig {
  override val jdbcUrl get() = "jdbc:mysql://${urlHost(host)}:$port/$database"
  override val driverClassName get() = "com.mysql.cj.jdbc.Driver"

  override val dataSourceProperties: Map<String, String>
    get() = buildMap {
      sslMode?.let { put("sslMode", it.name) }
      allowPublicKeyRetrieval?.let { put("allowPublicKeyRetrieval", it.toString()) }
      connectTimeout?.let { put("connectTimeout", it.inWholeMilliseconds.toString()) }
      socketTimeout?.let { put("socketTimeout", it.inWholeMilliseconds.toString()) }
      rewriteBatchedStatements?.let { put("rewriteBatchedStatements", it.toString()) }
      put("cachePrepStmts", cachePrepStmts.toString())
      if (cachePrepStmts) {
        put("prepStmtCacheSize", prepStmtCacheSize.toString())
        put("prepStmtCacheSqlLimit", prepStmtCacheSqlLimit.toString())
      }
    }

  // keep the password out of logs and exception messages
  override fun toString() =
    "MysqlConfig(host=$host, port=$port, database=$database, username=$username, " +
      "password=${password?.let { "****" }}, sslMode=$sslMode, allowPublicKeyRetrieval=$allowPublicKeyRetrieval, " +
      "connectTimeout=$connectTimeout, socketTimeout=$socketTimeout, " +
      "rewriteBatchedStatements=$rewriteBatchedStatements, cachePrepStmts=$cachePrepStmts, " +
      "prepStmtCacheSize=$prepStmtCacheSize, prepStmtCacheSqlLimit=$prepStmtCacheSqlLimit)"
}

/**
 * Connector/J's [SSL modes](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-security.html#cj-conn-prop_sslMode).
 * Only [VERIFY_CA] and [VERIFY_IDENTITY] protect against a server impersonating the real one; [VERIFY_IDENTITY]
 * also checks the host name.
 */
enum class MysqlSslMode { DISABLED, PREFERRED, REQUIRED, VERIFY_CA, VERIFY_IDENTITY }

/** A host for a JDBC URL: an IPv6 address must be bracketed, so its colons aren't read as the port. */
private fun urlHost(host: String) = if (':' in host && !host.startsWith("[")) "[$host]" else host
