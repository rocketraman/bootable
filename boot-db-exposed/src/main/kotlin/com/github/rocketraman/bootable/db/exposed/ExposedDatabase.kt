package com.github.rocketraman.bootable.db.exposed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import javax.sql.DataSource

/**
 * Adjusts the Exposed [DatabaseConfig] after the typed settings are applied, for Exposed features [ExposedConfig]
 * doesn't cover, such as a custom SQL logger, an explicit dialect, or a default schema.
 */
fun interface ExposedConfigCustomizer {
  fun customize(builder: DatabaseConfig.Builder)
}

/**
 * An Exposed [Database] over [dataSource], typically the pool's `JdbcDataSourceService.dataSource`, configured by
 * [config]. The settings belong to this `Database`, so they don't affect any other.
 *
 * Creating it opens no connection: the first transaction does.
 *
 * ```
 * val db = exposedDatabase(database.dataSource, binder.config("db.exposed"))
 * ```
 */
fun exposedDatabase(
  dataSource: DataSource,
  config: ExposedConfig = ExposedConfig(),
  customizer: ExposedConfigCustomizer = ExposedConfigCustomizer { },
): Database = Database.connect(dataSource, databaseConfig = databaseConfig(config, customizer))

internal fun databaseConfig(config: ExposedConfig, customizer: ExposedConfigCustomizer): DatabaseConfig =
  DatabaseConfig {
    config.defaultIsolationLevel?.let { defaultIsolationLevel = it.jdbcLevel }
    config.defaultReadOnly?.let { defaultReadOnly = it }
    config.defaultMaxAttempts?.let { defaultMaxAttempts = it }
    config.defaultMinRetryDelay?.let { defaultMinRetryDelay = it.inWholeMilliseconds }
    config.defaultMaxRetryDelay?.let { defaultMaxRetryDelay = it.inWholeMilliseconds }
    config.defaultQueryTimeout?.let { defaultQueryTimeout = it.inWholeSeconds.toInt() }
    config.defaultFetchSize?.let { defaultFetchSize = it }
    config.warnLongQueriesDuration?.let { warnLongQueriesDuration = it.inWholeMilliseconds }
    config.useNestedTransactions?.let { useNestedTransactions = it }
    customizer.customize(this)
  }

/**
 * Runs [statement] in a transaction on this database, on [Dispatchers.IO], since JDBC blocks. Use it from
 * coroutines, e.g. a Ktor route:
 *
 * ```
 * suspend fun get(name: String): String? = db.dbQuery {
 *   EntryTable.selectAll().where { EntryTable.name eq name }.singleOrNull()?.get(EntryTable.value)
 * }
 * ```
 *
 * [isolation] and [readOnly] override the database's defaults (from [ExposedConfig]) for this transaction only.
 * Called inside another transaction, it joins that transaction, as Exposed's `suspendTransaction` does.
 */
suspend fun <T> Database.dbQuery(
  isolation: TransactionIsolation? = null,
  readOnly: Boolean? = null,
  statement: suspend JdbcTransaction.() -> T,
): T = withContext(Dispatchers.IO) {
  suspendTransaction(this@dbQuery, isolation?.jdbcLevel, readOnly, statement)
}
