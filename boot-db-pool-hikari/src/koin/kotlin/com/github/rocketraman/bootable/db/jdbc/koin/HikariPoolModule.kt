package com.github.rocketraman.bootable.db.jdbc.koin

import com.github.rocketraman.bootable.config.koin.config
import com.github.rocketraman.bootable.db.jdbc.ConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.HikariConfigCustomizer
import com.github.rocketraman.bootable.db.jdbc.HikariConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.HikariPoolConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * A Koin module that provides a HikariCP [ConnectionPoolFactory], with its [HikariPoolConfig] decoded from the
 * `db.pool` config section. Include it alongside `jdbcDataSourceModule()`, which builds the pool with it. Requires a
 * `ConfigBinder` definition (e.g. from boot-config-hoplite's `hopliteConfigModule()`).
 *
 * To adjust HikariCP beyond [HikariPoolConfig], define a [HikariConfigCustomizer], with a qualifier of its own:
 *
 * ```
 * single<HikariConfigCustomizer>(named("registry")) { HikariConfigCustomizer { it.metricRegistry = registry } }
 * ```
 */
fun hikariPoolModule(): Module = module {
  config<HikariPoolConfig>("db.pool")
  single<ConnectionPoolFactory> {
    val customizers = getAll<HikariConfigCustomizer>()
    HikariConnectionPoolFactory(get()) { config -> customizers.forEach { it.customize(config) } }
  }
}
