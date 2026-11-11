@file:JvmName("HikariPoolInara")

package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config

/**
 * HikariCP, as `JdbcDataSourceModule(…)`'s [JdbcPool], with a [HikariPoolConfig] decoded from the `db.pool` config
 * section. To adjust HikariCP beyond [HikariPoolConfig], pass [customizers]:
 *
 * ```
 * HikariPool(setOf(HikariConfigCustomizer { it.metricRegistry = registry }))
 * ```
 */
class HikariPool(private val customizers: Set<HikariConfigCustomizer> = emptySet()) : JdbcPool {
  override fun connectionPoolFactory(configBinder: ConfigBinder): ConnectionPoolFactory =
    HikariConnectionPoolFactory(configBinder.config("db.pool")) { hikari ->
      customizers.forEach { it.customize(hikari) }
    }
}
