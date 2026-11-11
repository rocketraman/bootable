@file:JvmName("MysqlConfigSourceInara")

package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config

/**
 * MySQL, as `JdbcDataSourceModule(…)`'s [JdbcConfigSource], with a [MysqlConfig] decoded from the `db.jdbc`
 * config section.
 */
object MysqlConfigSource : JdbcConfigSource {
  override fun jdbcConfig(configBinder: ConfigBinder): JdbcConfig = configBinder.config<MysqlConfig>("db.jdbc")
}
