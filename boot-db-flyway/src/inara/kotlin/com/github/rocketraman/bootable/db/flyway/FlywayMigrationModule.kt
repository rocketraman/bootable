@file:JvmName("FlywayMigrationModuleInara")

package com.github.rocketraman.bootable.db.flyway

import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that migrates the `DataSource` of the [JdbcDataSourceModule] it requires. Build it with
 * `flywayMigrationModule()`, and compose it into the application's module. Its [flywayMigrationService] is one of the
 * application's services, so the database is migrated when the application starts.
 */
@DIModule
interface FlywayMigrationModule : @DIModule.Required JdbcDataSourceModule {
  val flywayConfig: FlywayConfig
  @AppServices val flywayMigrationService: FlywayMigrationService
}

/**
 * A [FlywayMigrationModule], with its [FlywayConfig] decoded from the `db.flyway` config section. To adjust Flyway
 * beyond [FlywayConfig], pass [customizers]:
 *
 * ```
 * flywayMigrationModule(setOf(FlywayConfigurationCustomizer { it.callbacks(audit) }))
 * ```
 */
fun flywayMigrationModule(
  customizers: Set<FlywayConfigurationCustomizer> = emptySet(),
): FlywayMigrationModulePart = FlywayMigrationModulePart(
  flywayConfig = config("db.flyway"),
  flywayMigrationService = Binding.single {
    FlywayMigrationService(dataSource, flywayConfig) { configuration ->
      customizers.forEach { it.customize(configuration) }
    }
  },
)
