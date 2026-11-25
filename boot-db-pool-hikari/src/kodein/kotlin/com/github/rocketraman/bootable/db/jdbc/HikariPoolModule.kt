package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.config.bindConfig
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
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val hikariPoolModule by DI.Module {
  bindSet<HikariConfigCustomizer>()
  bindConfig<HikariPoolConfig>("db.pool")
  bindSingleton<ConnectionPoolFactory> {
    val customizers = instance<Set<HikariConfigCustomizer>>()
    HikariConnectionPoolFactory(instance()) { config -> customizers.forEach { it.customize(config) } }
  }
}
