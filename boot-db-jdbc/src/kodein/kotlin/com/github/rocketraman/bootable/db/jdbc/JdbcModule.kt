package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton
import javax.sql.DataSource

/**
 * A Kodein module that binds the pooled [DataSource] and registers its [JdbcDataSourceService] into the
 * app-service set, so the pool starts and stops with the application.
 *
 * It needs two bindings, one for each choice the application makes:
 * - the driver's [JdbcConfig]: import a driver module's config module such as `sqliteConfigModule` from
 *   `boot-db-jdbc-sqlite`, or [jdbcGenericConfigModule] for any database by URL.
 * - the pool's [ConnectionPoolFactory]: import a pool module such as `hikariPoolModule` from
 *   `boot-db-pool-hikari`.
 */
val jdbcDataSourceModule by DI.Module {
  bindSingleton { JdbcDataSourceService(instance(), instance()) }
  bindSingleton<DataSource> { instance<JdbcDataSourceService>().dataSource }
  inBindSet<AppService> { add { singleton { instance<JdbcDataSourceService>() } } }
}

/**
 * A Kodein module that decodes a [JdbcGenericConfig] from the `db.jdbc` config section as the [JdbcConfig].
 * Requires a `ConfigBinder` binding (e.g. from boot-config-hoplite's `hopliteConfigModule`).
 */
val jdbcGenericConfigModule by DI.Module {
  bindSingleton<JdbcConfig> { instance<ConfigBinder>().config<JdbcGenericConfig>("db.jdbc") }
}
