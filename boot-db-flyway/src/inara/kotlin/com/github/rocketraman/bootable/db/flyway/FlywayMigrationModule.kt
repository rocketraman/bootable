@file:JvmName("FlywayMigrationModuleInara")

package com.github.rocketraman.bootable.db.flyway

import com.github.rocketraman.bootable.boot.AppServiceAcceptor
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that migrates the database. Build it with the `FlywayMigrationModule(jdbc, …)` builder,
 * which adds its [flywayMigrationService] to the application's services, so the database is migrated when the
 * application starts.
 */
@DIModule
interface FlywayMigrationModule {
  val flywayConfig: FlywayConfig
  val flywayMigrationService: FlywayMigrationService
}

/**
 * A [FlywayMigrationModule] that migrates [jdbc]'s `DataSource`, with its [FlywayConfig] decoded from the `db.flyway`
 * section of [context]'s config, and adding its service to [context], such as the `BootableModule`. To adjust Flyway
 * beyond [FlywayConfig], pass [customizers]:
 *
 * ```
 * FlywayMigrationModule(jdbc, bootable, setOf(FlywayConfigurationCustomizer { it.callbacks(audit) }))
 * ```
 */
fun <C> FlywayMigrationModule(
  jdbc: JdbcDataSourceModule,
  context: C,
  customizers: Set<FlywayConfigurationCustomizer> = emptySet(),
): FlywayMigrationModule where C : ConfigModule, C : AppServiceAcceptor {
  val module = FlywayMigrationModule(
    flywayConfig = context.config("db.flyway"),
    flywayMigrationService = Binding.single {
      FlywayMigrationService(jdbc.dataSource, flywayConfig) { configuration ->
        customizers.forEach { it.customize(configuration) }
      }
    },
  )
  context.addAppService { module.flywayMigrationService }
  return module
}
