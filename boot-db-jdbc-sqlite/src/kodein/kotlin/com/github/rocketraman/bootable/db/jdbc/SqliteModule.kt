package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance

/**
 * A Kodein module that decodes a [SqliteConfig] from the [configKey] section as the [JdbcConfig].
 * Import it alongside `jdbcDataSourceModule`, which builds the pool from it. Requires a `ConfigBinder` binding
 * (e.g. from boot-config-hoplite's `hopliteConfigModule`).
 */
fun sqliteConfigModule(configKey: String = "db") = DI.Module("sqliteConfigModule") {
  bindSingleton<JdbcConfig> { instance<ConfigBinder>().config<SqliteConfig>(configKey) }
}
