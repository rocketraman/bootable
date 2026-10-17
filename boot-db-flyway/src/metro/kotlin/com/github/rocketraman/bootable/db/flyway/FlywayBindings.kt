package com.github.rocketraman.bootable.db.flyway

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import javax.sql.DataSource

/**
 * Registers a [FlywayMigrationService] into the app-service set, so the database is migrated when the application
 * starts, with its [FlywayConfig] decoded from the `db.flyway` config section. It migrates the `DataSource` that
 * `JdbcDataSourceBindings` (from boot-db-jdbc-metro) provides.
 *
 * To adjust Flyway beyond [FlywayConfig], contribute a [FlywayConfigurationCustomizer]:
 *
 * ```
 * @Provides @IntoSet fun audit(audit: AuditCallback) = FlywayConfigurationCustomizer { it.callbacks(audit) }
 * ```
 */
@ContributesTo(BootScope::class)
interface FlywayBindings {
  @Multibinds(allowEmpty = true) val flywayConfigurationCustomizers: Set<FlywayConfigurationCustomizer>

  companion object {
    @Provides @SingleIn(BootScope::class)
    fun flywayConfig(binder: ConfigBinder): FlywayConfig = binder.config("db.flyway")

    @Provides @SingleIn(BootScope::class)
    fun flywayMigrationService(
      dataSource: DataSource,
      config: FlywayConfig,
      customizers: Set<FlywayConfigurationCustomizer>,
    ): FlywayMigrationService =
      FlywayMigrationService(dataSource, config) { configuration -> customizers.forEach { it.customize(configuration) } }

    @Provides @IntoSet fun flywayMigrationAppService(service: FlywayMigrationService): AppService = service
  }
}
