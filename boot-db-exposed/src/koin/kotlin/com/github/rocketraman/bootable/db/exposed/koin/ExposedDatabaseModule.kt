package com.github.rocketraman.bootable.db.exposed.koin

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.config.koin.config
import com.github.rocketraman.bootable.db.exposed.ExposedConfig
import com.github.rocketraman.bootable.db.exposed.ExposedConfigCustomizer
import com.github.rocketraman.bootable.db.exposed.ExposedDatabaseService
import com.github.rocketraman.bootable.db.exposed.SchemaVerificationService
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that defines an Exposed [Database] over the pooled `DataSource`, with its [ExposedConfig] decoded from
 * the `db.exposed` config section, and adds its [ExposedDatabaseService] and a [SchemaVerificationService] to the app
 * services. Include it alongside `jdbcDataSourceModule()`, which provides the `DataSource`. Requires a `ConfigBinder`
 * definition (e.g. from boot-config-hoplite's `hopliteConfigModule()`).
 *
 * Define each of your tables as a [Table], with a qualifier of its own, so its schema is verified when the application
 * starts:
 *
 * ```
 * single<Table>(named<EntryTable>()) { EntryTable }
 * ```
 *
 * To adjust Exposed beyond [ExposedConfig], define an [ExposedConfigCustomizer], in the same way.
 */
fun exposedDatabaseModule(): Module = module {
  config<ExposedConfig>("db.exposed")
  single {
    val customizers = getAll<ExposedConfigCustomizer>()
    ExposedDatabaseService(get(), get()) { builder -> customizers.forEach { it.customize(builder) } }
  }
  single<Database> { get<ExposedDatabaseService>().database }
  single<AppService>(named<ExposedDatabaseService>()) { get<ExposedDatabaseService>() }
  single { SchemaVerificationService(get(), getAll<Table>().toSet(), get<ExposedConfig>().schemaVerification) }
  single<AppService>(named<SchemaVerificationService>()) { get<SchemaVerificationService>() }
}
