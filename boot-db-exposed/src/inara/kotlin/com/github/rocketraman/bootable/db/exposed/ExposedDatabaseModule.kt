@file:JvmName("ExposedDatabaseModuleInara")

package com.github.rocketraman.bootable.db.exposed

import com.github.rocketraman.bootable.boot.AppServiceAcceptor
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceModule
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that provides an Exposed [Database]. Build it with the `ExposedDatabaseModule(jdbc, …)` builder,
 * which adds its [exposedDatabaseService] and [schemaVerificationService] to the application's services.
 */
@DIModule
interface ExposedDatabaseModule {
  val exposedConfig: ExposedConfig
  val exposedDatabaseService: ExposedDatabaseService
  val database: Database get() = exposedDatabaseService.database
  val schemaVerificationService: SchemaVerificationService
}

/**
 * An [ExposedDatabaseModule] over [jdbc]'s `DataSource`, with its [ExposedConfig] decoded from the `db.exposed`
 * section of [context]'s config, verifying the schema of [tables] when the application starts, and adding its services
 * to [context], such as the `BootableModule`. To adjust Exposed beyond [ExposedConfig], pass [customizers].
 */
fun <C> ExposedDatabaseModule(
  jdbc: JdbcDataSourceModule,
  tables: Set<Table>,
  context: C,
  customizers: Set<ExposedConfigCustomizer> = emptySet(),
): ExposedDatabaseModule where C : ConfigModule, C : AppServiceAcceptor {
  val module = ExposedDatabaseModule(
    exposedConfig = context.config("db.exposed"),
    exposedDatabaseService = Binding.single {
      ExposedDatabaseService(jdbc.dataSource, exposedConfig) { builder ->
        customizers.forEach { it.customize(builder) }
      }
    },
    schemaVerificationService = Binding.single {
      SchemaVerificationService(database, tables, exposedConfig.schemaVerification)
    },
  )
  context.addAppService { module.exposedDatabaseService }
  context.addAppService { module.schemaVerificationService }
  return module
}
