package com.github.rocketraman.bootable.db.exposed

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.kodein.di.DI
import org.kodein.di.bindSet
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that binds an Exposed [Database] over the pooled `DataSource`, with its [ExposedConfig] decoded
 * from the `db.exposed` config section, and registers its [ExposedDatabaseService] and a [SchemaVerificationService]
 * into the app-service set. Import
 * it alongside `jdbcDataSourceModule`, which provides the `DataSource`. Requires a `ConfigBinder` binding (e.g. from
 * boot-config-hoplite's `hopliteConfigModule`).
 *
 * Add your tables to the set this module opens, so their schema is verified when the application starts:
 *
 * ```
 * inBindSet<Table> { add { singleton { EntryTable } } }
 * ```
 *
 * To adjust Exposed beyond [ExposedConfig], add an [ExposedConfigCustomizer] to its set, in the same way.
 */
val exposedDatabaseModule by DI.Module {
  bindSet<Table>()
  bindSet<ExposedConfigCustomizer>()
  bindSingleton<ExposedConfig> { instance<ConfigBinder>().config("db.exposed") }
  bindSingleton {
    val customizers = instance<Set<ExposedConfigCustomizer>>()
    ExposedDatabaseService(instance(), instance()) { builder -> customizers.forEach { it.customize(builder) } }
  }
  bindSingleton<Database> { instance<ExposedDatabaseService>().database }
  inBindSet<AppService> { add { singleton { instance<ExposedDatabaseService>() } } }
  bindSingleton { SchemaVerificationService(instance(), instance(), instance<ExposedConfig>().schemaVerification) }
  inBindSet<AppService> { add { singleton { instance<SchemaVerificationService>() } } }
}
