package com.github.rocketraman.bootable.db.exposed

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.db.jdbc.DbMilestones
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import javax.sql.DataSource

/**
 * Owns the application's Exposed [Database] over [dataSource], typically the pool's
 * `JdbcDataSourceService.dataSource`, and ties it to the application lifecycle. [config] applies to this
 * `Database` only, so it doesn't affect any other.
 *
 * [database] can be handed to other components as soon as this is constructed: creating it opens no connection,
 * the first transaction does. Exposed registers every `Database` in a process-wide registry, which it uses to pick a
 * transaction's database when none is named; shutdown unregisters this one, so a transaction after shutdown, or in
 * a later boot in the same JVM, can't pick it up once its pool is closed.
 *
 * The service is in the [DefaultPhase.Infrastructure] phase, after [DbMilestones.DataSourceReady], so it shuts down
 * after the services that use the database, in later phases, and before the pool closes.
 *
 * ```
 * val exposed = ExposedDatabaseService(database.dataSource, binder.config("db.exposed"))
 * ```
 */
class ExposedDatabaseService(
  dataSource: DataSource,
  config: ExposedConfig = ExposedConfig(),
  customizer: ExposedConfigCustomizer = ExposedConfigCustomizer { },
) : AppService {

  /** The Exposed `Database`. Bind this, not the service, into anything that runs transactions. */
  val database: Database = Database.connect(dataSource, databaseConfig = databaseConfig(config, customizer))

  override fun shutdown() {
    TransactionManager.closeAndUnregister(database)
  }

  override fun ordering() = Ordering(DefaultPhase.Infrastructure, after = setOf(DbMilestones.DataSourceReady))
}
