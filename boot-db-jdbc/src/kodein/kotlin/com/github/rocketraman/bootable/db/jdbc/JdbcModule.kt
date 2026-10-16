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
 * It needs a [JdbcConfig] binding: import [jdbcGenericConfigModule], or a driver module's config module
 * such as `sqliteConfigModule` from `boot-db-jdbc-sqlite`.
 */
val jdbcDataSourceModule by DI.Module {
  bindSingleton { JdbcDataSourceService(instance()) }
  bindSingleton<DataSource> { instance<JdbcDataSourceService>().dataSource }
  inBindSet<AppService> { add { singleton { instance<JdbcDataSourceService>() } } }
}

/**
 * A Kodein module that decodes a [JdbcGenericConfig] from the [configKey] section as the [JdbcConfig].
 * Requires a `ConfigBinder` binding (e.g. from boot-config-hoplite's `hopliteConfigModule`).
 */
fun jdbcGenericConfigModule(configKey: String = "db") = DI.Module("jdbcGenericConfigModule") {
  bindSingleton<JdbcConfig> { instance<ConfigBinder>().config<JdbcGenericConfig>(configKey) }
}
