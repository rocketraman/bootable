package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.config.Masked

/**
 * A [JdbcConfig] for any database, configured by JDBC URL. The JDBC driver must be on the classpath.
 *
 * ```
 * db {
 *   jdbc {
 *     jdbcUrl = "jdbc:postgresql://localhost:5432/app"
 *     username = "app"
 *     password = ${?DB_PASSWORD}
 *   }
 *   pool { maximumPoolSize = 20 }  # read by the pool module
 * }
 * ```
 */
data class JdbcGenericConfig(
  override val jdbcUrl: String,
  override val driverClassName: String? = null,
  override val username: String? = null,
  override val password: Masked? = null,
  override val dataSourceProperties: Map<String, String> = emptyMap(),
) : JdbcConfig
