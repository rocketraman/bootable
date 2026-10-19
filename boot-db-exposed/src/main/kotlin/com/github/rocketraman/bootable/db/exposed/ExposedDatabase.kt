package com.github.rocketraman.bootable.db.exposed

import com.github.rocketraman.bootable.db.jdbc.TransactionIsolation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/**
 * Adjusts the Exposed [DatabaseConfig] after the typed settings are applied, for Exposed features [ExposedConfig]
 * doesn't cover, such as a custom SQL logger, an explicit dialect, or a default schema.
 */
fun interface ExposedConfigCustomizer {
  fun customize(builder: DatabaseConfig.Builder)
}

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
 * Runs [statement] in a transaction on this database, using Exposed's JDBC `suspendTransaction`, and returns its
 * result. Use it from coroutines, e.g. a Ktor route or a repository method:
 *
 * ```
 * suspend fun get(name: String): String? = db.withTransaction {
 *   EntryTable.selectAll().where { EntryTable.name eq name }.singleOrNull()?.get(EntryTable.value)
 * }
 * ```
 *
 * The transaction commits when [statement] returns, and rolls back when it throws. [isolation] and [readOnly]
 * override the database's defaults (from [ExposedConfig]) for this transaction only.
 *
 * ## Composing transactions
 *
 * Called inside another transaction on the same coroutine, e.g. an outer `withTransaction`, it joins that transaction
 * instead of starting one, so a caller can make several calls, each its own transaction when called alone, into one
 * unit of work:
 *
 * ```
 * db.withTransaction {
 *   accounts.debit(from, amount)  // each a withTransaction of its own...
 *   accounts.credit(to, amount)   // ...joining this one here
 * }                               // one commit, or one rollback
 * ```
 *
 * A joining call never commits: the outermost transaction does, once. Mind that:
 * - Its [isolation] and [readOnly] are ignored: the outer transaction's settings apply.
 * - Don't catch an inner failure and carry on. On an `SQLException`, Exposed rolls back the whole shared transaction,
 *   even from a joining call, so the outer transaction's earlier work is lost while its later work still commits. To
 *   recover from an inner failure, set [ExposedConfig.useNestedTransactions]: each joining call then runs in a
 *   savepoint, and a failure rolls back only that savepoint.
 * - Don't run statements in parallel within one transaction, e.g. from `async` blocks: a child coroutine inherits the
 *   transaction, and its one JDBC connection doesn't support concurrent statements.
 *
 * ## Threads
 *
 * The transaction follows the coroutine across threads, but its JDBC statements block, so they run on this
 * database's [DatabaseConfig.dispatcher]: [Dispatchers.IO] unless an [ExposedConfigCustomizer] sets another, such as
 * a virtual-thread dispatcher (worthwhile on JDK 24+, but not for SQLite, whose driver blocks in native code) or a
 * `Dispatchers.IO.limitedParallelism(n)` that keeps database waits apart from other IO work. Either way, the
 * connection pool, not the dispatcher, limits how many statements run at once. This is not R2DBC: no I/O is
 * non-blocking.
 *
 * The switch to the dispatcher is explicit because Exposed only applies it when the caller has no dispatcher of its
 * own, and a Ktor route always has one.
 */
suspend fun <T> Database.withTransaction(
  isolation: TransactionIsolation? = null,
  readOnly: Boolean? = null,
  statement: suspend JdbcTransaction.() -> T,
): T = withContext(config.dispatcher) {
  suspendTransaction(this@withTransaction, isolation?.jdbcLevel, readOnly, statement)
}
