package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import org.kodein.di.DI
import org.kodein.di.bindSet
import org.kodein.di.bindSingleton
import org.kodein.di.instance

/**
 * A Kodein module that provides a HikariCP [ConnectionPoolFactory], with its [HikariPoolConfig] decoded from the
 * `db.pool` config section. Import it alongside `jdbcDataSourceModule`, which builds the pool with it. Requires a
 * `ConfigBinder` binding (e.g. from boot-config-hoplite's `hopliteConfigModule`).
 *
 * To adjust HikariCP beyond [HikariPoolConfig], add a [HikariConfigCustomizer] to the set this module opens:
 *
 * ```
 * inBindSet<HikariConfigCustomizer> { add { singleton { HikariConfigCustomizer { it.metricRegistry = registry } } } }
 * ```
 */
val hikariPoolModule by DI.Module {
  bindSet<HikariConfigCustomizer>()
  bindSingleton<HikariPoolConfig> { instance<ConfigBinder>().config("db.pool") }
  bindSingleton<ConnectionPoolFactory> {
    val customizers = instance<Set<HikariConfigCustomizer>>()
    HikariConnectionPoolFactory(instance()) { config -> customizers.forEach { it.customize(config) } }
  }
}
