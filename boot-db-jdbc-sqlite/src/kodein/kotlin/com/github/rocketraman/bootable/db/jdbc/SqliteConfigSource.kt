package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.config.bindConfigAs
import org.kodein.di.DI

/**
 * A Kodein module that decodes a [SqliteConfig] from the `db.jdbc` config section as the [JdbcConfig].
 * Import it alongside `jdbcDataSourceModule`, which builds the pool from it, and a pool module. Requires a `ConfigBinder` binding
 * (e.g. from boot-config-hoplite's `hopliteConfigModule`).
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val sqliteConfigModule by DI.Module {
  bindConfigAs<JdbcConfig, SqliteConfig>("db.jdbc")
}
