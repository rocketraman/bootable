package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Provides a HikariCP [ConnectionPoolFactory], with its [HikariPoolConfig] decoded from the `db.pool` config
 * section, for `JdbcDataSourceBindings` (from boot-db-jdbc-metro) to build the pool with.
 *
 * To adjust HikariCP beyond [HikariPoolConfig], contribute a [HikariConfigCustomizer]:
 *
 * ```
 * @Provides @IntoSet fun metrics(registry: MeterRegistry) = HikariConfigCustomizer { it.metricRegistry = registry }
 * ```
 */
@ContributesTo(BootScope::class)
interface HikariPoolBindings {
  @Multibinds(allowEmpty = true) val hikariConfigCustomizers: Set<HikariConfigCustomizer>

  companion object {
    @Provides @SingleIn(BootScope::class)
    fun hikariPoolConfig(binder: ConfigBinder): HikariPoolConfig = binder.config("db.pool")

    @Provides @SingleIn(BootScope::class)
    fun hikariConnectionPoolFactory(
      poolConfig: HikariPoolConfig,
      customizers: Set<HikariConfigCustomizer>,
    ): ConnectionPoolFactory =
      HikariConnectionPoolFactory(poolConfig) { config -> customizers.forEach { it.customize(config) } }
  }
}
