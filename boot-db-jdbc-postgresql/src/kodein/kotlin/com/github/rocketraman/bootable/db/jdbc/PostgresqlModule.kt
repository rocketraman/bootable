package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance

/**
 * A Kodein module that decodes a [PostgresqlConfig] from the `db.jdbc` config section as the [JdbcConfig].
 * Import it alongside `jdbcDataSourceModule`, which builds the pool from it, and a pool module. Requires a `ConfigBinder` binding
 * (e.g. from boot-config-hoplite's `hopliteConfigModule`).
 */
val postgresqlConfigModule by DI.Module {
  bindSingleton<JdbcConfig> { instance<ConfigBinder>().config<PostgresqlConfig>("db.jdbc") }
}
