package com.github.rocketraman.bootable.db.exposed

import java.sql.Connection
import kotlin.time.Duration

/**
 * Settings for the Exposed [org.jetbrains.exposed.v1.jdbc.Database]. Every field but [schemaVerification] is
 * optional; an unset field keeps Exposed's default. They apply to this `Database` only, as its `DatabaseConfig`,
 * and an individual transaction can still override them.
 *
 * Decoded from the `db.exposed` config section:
 *
 * ```
 * db {
 *   jdbc { ... }
 *   exposed {
 *     defaultIsolationLevel = READ_COMMITTED
 *     warnLongQueriesDuration = 2s
 *   }
 * }
 * ```
 *
 * Exposed logs every SQL statement at DEBUG on its `Exposed` logger, so turn SQL logging on and off in the logging
 * config. For an Exposed setting not listed here, such as a custom SQL logger or an explicit dialect, use an
 * [ExposedConfigCustomizer].
 */
data class ExposedConfig(
  /** The isolation level of a transaction that doesn't set its own. Defaults to the database's own default. */
  val defaultIsolationLevel: TransactionIsolation? = null,
  /**
   * Whether a transaction that doesn't say otherwise is read-only. Exposed's default is `false`. Not for SQLite,
   * whose driver can't make an open connection read-only.
   */
  val defaultReadOnly: Boolean? = null,
  /**
   * How many times a transaction is attempted before its `SQLException` is thrown. Exposed's default is 3; set 1 to
   * never retry.
   */
  val defaultMaxAttempts: Int? = null,
  /** The shortest wait before retrying a failed transaction. Exposed's default is no wait. */
  val defaultMinRetryDelay: Duration? = null,
  /** The longest wait before retrying a failed transaction. Exposed's default is no wait. */
  val defaultMaxRetryDelay: Duration? = null,
  /** How long a statement may run before the driver cancels it, where the driver supports it. No limit by default. */
  val defaultQueryTimeout: Duration? = null,
  /** How many rows a query fetches from the database at a time. Defaults to the driver's default. */
  val defaultFetchSize: Int? = null,
  /** Queries that run longer than this are logged at WARN. Off by default. */
  val warnLongQueriesDuration: Duration? = null,
  /** Whether a transaction inside another is a real nested transaction (a savepoint). Off by default. */
  val useNestedTransactions: Boolean? = null,
  /** What [SchemaVerificationService] does when the database schema doesn't match the registered tables. */
  val schemaVerification: SchemaVerification = SchemaVerification.FAIL,
)

/** The JDBC transaction isolation levels, as [Connection]'s `TRANSACTION_*` constants. */
enum class TransactionIsolation(val jdbcLevel: Int) {
  READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),
  READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
  REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
  SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE),
}

/** What [SchemaVerificationService] does with a schema that doesn't match the registered tables. */
enum class SchemaVerification {
  /** Fail the application's start, listing the SQL the schema needs. */
  FAIL,

  /** Log the SQL the schema needs at WARN, and carry on. */
  WARN,

  /** Don't check the schema. */
  OFF,
}
