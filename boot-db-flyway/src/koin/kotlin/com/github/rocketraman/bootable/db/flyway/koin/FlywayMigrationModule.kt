package com.github.rocketraman.bootable.db.flyway.koin

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.flyway.FlywayConfig
import com.github.rocketraman.bootable.db.flyway.FlywayConfigurationCustomizer
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that adds a [FlywayMigrationService] to the app services, so the database is migrated when the
 * application starts, with its [FlywayConfig] decoded from the `db.flyway` config section. Include it alongside
 * `jdbcDataSourceModule()`, which provides the `DataSource` it migrates. Requires a `ConfigBinder` definition (e.g.
 * from boot-config-hoplite's `hopliteConfigModule()`).
 *
 * To adjust Flyway beyond [FlywayConfig], define a [FlywayConfigurationCustomizer], with a qualifier of its own:
 *
 * ```
 * single<FlywayConfigurationCustomizer>(named("audit")) { FlywayConfigurationCustomizer { it.callbacks(audit) } }
 * ```
 */
fun flywayMigrationModule(): Module = module {
  single<FlywayConfig> { get<ConfigBinder>().config("db.flyway") }
  single {
    val customizers = getAll<FlywayConfigurationCustomizer>()
    FlywayMigrationService(get(), get()) { configuration -> customizers.forEach { it.customize(configuration) } }
  }
  single<AppService>(named<FlywayMigrationService>()) { get<FlywayMigrationService>() }
}
