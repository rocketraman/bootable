package com.github.rocketraman.bootable.db.flyway

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import org.kodein.di.DI
import org.kodein.di.bindSet
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that registers a [FlywayMigrationService] into the app-service set, so the database is migrated
 * when the application starts, with its [FlywayConfig] decoded from the `db.flyway` config section. Import it
 * alongside `jdbcDataSourceModule`, which provides the `DataSource` it migrates. Requires a `ConfigBinder` binding
 * (e.g. from boot-config-hoplite's `hopliteConfigModule`).
 *
 * To adjust Flyway beyond [FlywayConfig], add a [FlywayConfigurationCustomizer] to the set this module opens:
 *
 * ```
 * inBindSet<FlywayConfigurationCustomizer> { add { singleton { FlywayConfigurationCustomizer { it.callbacks(audit) } } } }
 * ```
 */
val flywayMigrationModule by DI.Module {
  bindSet<FlywayConfigurationCustomizer>()
  bindSingleton<FlywayConfig> { instance<ConfigBinder>().config("db.flyway") }
  bindSingleton {
    val customizers = instance<Set<FlywayConfigurationCustomizer>>()
    FlywayMigrationService(instance(), instance()) { configuration -> customizers.forEach { it.customize(configuration) } }
  }
  inBindSet<AppService> { add { singleton { instance<FlywayMigrationService>() } } }
}
