@file:JvmName("SqliteConfigSourceInara")

package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config

/**
 * SQLite, as `JdbcDataSourceModule(…)`'s [JdbcConfigSource], with a [SqliteConfig] decoded from the `db.jdbc`
 * config section.
 */
object SqliteConfigSource : JdbcConfigSource {
  override fun jdbcConfig(configBinder: ConfigBinder): JdbcConfig = configBinder.config<SqliteConfig>("db.jdbc")
}
