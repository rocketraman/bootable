package com.github.rocketraman.bootable.db.jdbc.koin

import com.github.rocketraman.bootable.config.koin.configAs
import com.github.rocketraman.bootable.db.jdbc.JdbcConfig
import com.github.rocketraman.bootable.db.jdbc.PostgresqlConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * A Koin module that decodes a [PostgresqlConfig] from the `db.jdbc` config section as the [JdbcConfig]. Include it
 * alongside `jdbcDataSourceModule()`, which builds the pool from it, and a pool module. Requires a `ConfigBinder`
 * definition (e.g. from boot-config-hoplite's `hopliteConfigModule()`).
 */
fun postgresqlConfigModule(): Module = module {
  configAs<JdbcConfig, PostgresqlConfig>("db.jdbc")
}
