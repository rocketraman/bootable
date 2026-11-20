@file:JvmName("ExposedDatabaseModuleInara")

package com.github.rocketraman.bootable.db.exposed

import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceModule
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that provides an Exposed [Database] over the `DataSource` of the [JdbcDataSourceModule] it
 * requires. Build it with `exposedDatabaseModule(tables)`, and compose it into the application's module. Its
 * [exposedDatabaseService] and [schemaVerificationService] are among the application's services.
 */
@DIModule
interface ExposedDatabaseModule : @DIModule.Required JdbcDataSourceModule {
  val exposedConfig: ExposedConfig
  @AppServices val exposedDatabaseService: ExposedDatabaseService
  val database: Database get() = exposedDatabaseService.database
  @AppServices val schemaVerificationService: SchemaVerificationService
}

/**
 * An [ExposedDatabaseModule], with its [ExposedConfig] decoded from the `db.exposed` config section, verifying the
 * schema of [tables] when the application starts. To adjust Exposed beyond [ExposedConfig], pass [customizers].
 */
fun exposedDatabaseModule(
  tables: Set<Table>,
  customizers: Set<ExposedConfigCustomizer> = emptySet(),
): ExposedDatabaseModulePart = ExposedDatabaseModulePart(
  exposedConfig = config("db.exposed"),
  exposedDatabaseService = Binding.single {
    ExposedDatabaseService(dataSource, exposedConfig) { builder ->
      customizers.forEach { it.customize(builder) }
    }
  },
  schemaVerificationService = Binding.single {
    SchemaVerificationService(database, tables, exposedConfig.schemaVerification)
  },
)
