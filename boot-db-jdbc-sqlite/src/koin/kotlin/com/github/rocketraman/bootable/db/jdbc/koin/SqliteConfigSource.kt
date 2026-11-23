package com.github.rocketraman.bootable.db.jdbc.koin

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.jdbc.JdbcConfig
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * A Koin module that decodes a [SqliteConfig] from the `db.jdbc` config section as the [JdbcConfig]. Include it
 * alongside `jdbcDataSourceModule()`, which builds the pool from it, and a pool module. Requires a `ConfigBinder`
 * definition (e.g. from boot-config-hoplite's `hopliteConfigModule()`).
 */
fun sqliteConfigModule(): Module = module {
  single<JdbcConfig> { get<ConfigBinder>().config<SqliteConfig>("db.jdbc") }
}
