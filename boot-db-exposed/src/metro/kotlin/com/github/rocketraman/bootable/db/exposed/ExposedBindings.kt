package com.github.rocketraman.bootable.db.exposed

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

/**
 * Provides an Exposed [Database] over the `DataSource` from `JdbcDataSourceBindings` (boot-db-jdbc-metro), with its
 * [ExposedConfig] decoded from the `db.exposed` config section, and registers its [ExposedDatabaseService] and a
 * [SchemaVerificationService] into the app-service set.
 *
 * Contribute your tables, so their schema is verified when the application starts:
 *
 * ```
 * @Provides @IntoSet fun entryTable(): Table = EntryTable
 * ```
 *
 * To adjust Exposed beyond [ExposedConfig], contribute an [ExposedConfigCustomizer] in the same way.
 */
@ContributesTo(BootScope::class)
interface ExposedBindings {
  @Multibinds(allowEmpty = true) val exposedTables: Set<Table>

  @Multibinds(allowEmpty = true) val exposedConfigCustomizers: Set<ExposedConfigCustomizer>

  companion object {
    @Provides @SingleIn(BootScope::class)
    fun exposedConfig(binder: ConfigBinder): ExposedConfig = binder.config("db.exposed")

    @Provides @SingleIn(BootScope::class)
    fun exposedDatabaseService(
      dataSource: DataSource,
      config: ExposedConfig,
      customizers: Set<ExposedConfigCustomizer>,
    ): ExposedDatabaseService =
      ExposedDatabaseService(dataSource, config) { builder -> customizers.forEach { it.customize(builder) } }

    @Provides fun database(service: ExposedDatabaseService): Database = service.database

    @Provides @IntoSet fun exposedDatabaseAppService(service: ExposedDatabaseService): AppService = service

    @Provides @SingleIn(BootScope::class)
    fun schemaVerificationService(database: Database, tables: Set<Table>, config: ExposedConfig): SchemaVerificationService =
      SchemaVerificationService(database, tables, config.schemaVerification)

    @Provides @IntoSet fun schemaVerificationAppService(service: SchemaVerificationService): AppService = service
  }
}
