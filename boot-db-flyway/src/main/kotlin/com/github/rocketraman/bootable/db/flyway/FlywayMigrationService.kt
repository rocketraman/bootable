package com.github.rocketraman.bootable.db.flyway

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import org.apache.logging.log4j.kotlin.logger
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import javax.sql.DataSource

/**
 * Adjusts Flyway's [FluentConfiguration] after the typed settings are applied, for Flyway features [FlywayConfig]
 * doesn't cover, such as callbacks or Java migrations.
 */
fun interface FlywayConfigurationCustomizer {
  fun customize(configuration: FluentConfiguration)
}

/**
 * Migrates the database with Flyway when the application starts.
 *
 * It starts at [PRIORITY], just below [JdbcDataSourceService.PRIORITY], so it runs after the pool has started and
 * validated its connection, and before the services that use the schema. A failed migration fails the start.
 *
 * ```
 * FlywayMigrationService(database.dataSource, binder.config("db.flyway"))
 * ```
 */
class FlywayMigrationService(
  dataSource: DataSource,
  config: FlywayConfig = FlywayConfig(),
  customizer: FlywayConfigurationCustomizer = FlywayConfigurationCustomizer { },
) : AppService {

  /** The configured Flyway instance, e.g. for its `info()`. Creating it opens no connection. */
  val flyway: Flyway = flywayConfiguration(dataSource, config).also(customizer::customize).load()

  override fun start() {
    val result = flyway.migrate()
    if (result.migrationsExecuted == 0) {
      log.info { "Database schema is up to date at version ${result.initialSchemaVersion ?: "<empty>"}" }
    } else {
      log.info {
        "Applied ${result.migrationsExecuted} migration(s) to the database schema, from version " +
          "${result.initialSchemaVersion ?: "<empty>"} to ${result.targetSchemaVersion}"
      }
    }
    result.warnings.forEach { log.warn { it } }
  }

  override fun priority() = PRIORITY

  companion object {
    /**
     * Just below [JdbcDataSourceService.PRIORITY], so migrations run once the pool is up, and before the services
     * that use the database, including `boot-db-exposed`'s schema verification at a lower priority still.
     */
    const val PRIORITY = JdbcDataSourceService.PRIORITY - 100

    private val log = logger()
  }
}

internal fun flywayConfiguration(dataSource: DataSource, config: FlywayConfig): FluentConfiguration =
  Flyway.configure().dataSource(dataSource).apply {
    config.locations?.let { locations(*it.toTypedArray()) }
    config.failOnMissingLocations?.let { failOnMissingLocations(it) }
    config.defaultSchema?.let { defaultSchema(it) }
    config.schemas?.let { schemas(*it.toTypedArray()) }
    config.table?.let { table(it) }
    config.baselineOnMigrate?.let { baselineOnMigrate(it) }
    config.baselineVersion?.let { baselineVersion(it) }
    config.validateOnMigrate?.let { validateOnMigrate(it) }
    config.validateMigrationNaming?.let { validateMigrationNaming(it) }
    config.outOfOrder?.let { outOfOrder(it) }
    config.target?.let { target(it) }
    config.cleanDisabled?.let { cleanDisabled(it) }
    if (config.placeholders.isNotEmpty()) placeholders(config.placeholders)
  }
