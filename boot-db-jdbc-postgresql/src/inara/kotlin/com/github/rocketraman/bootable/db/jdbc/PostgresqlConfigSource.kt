@file:JvmName("PostgresqlConfigSourceInara")

package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config

/**
 * PostgreSQL, as `JdbcDataSourceModule(…)`'s [JdbcConfigSource], with a [PostgresqlConfig] decoded from the `db.jdbc`
 * config section.
 */
object PostgresqlConfigSource : JdbcConfigSource {
  override fun jdbcConfig(configBinder: ConfigBinder): JdbcConfig = configBinder.config<PostgresqlConfig>("db.jdbc")
}
