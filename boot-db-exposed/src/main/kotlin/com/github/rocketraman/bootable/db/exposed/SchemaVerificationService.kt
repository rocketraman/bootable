package com.github.rocketraman.bootable.db.exposed

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.db.jdbc.DbMilestones
import org.apache.logging.log4j.kotlin.logger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

/**
 * Checks, when the application starts, that the database schema matches the Exposed [tables], so a missing
 * migration fails the start rather than the first query that needs it.
 *
 * The check compares tables, columns, column types, primary keys, indexes, and foreign keys, using Exposed's
 * `MigrationUtils.statementsRequiredForDatabaseMigration`, and reports the SQL that would make the schema match. With
 * [SchemaVerification.FAIL] it throws a [SchemaVerificationException]; with [SchemaVerification.WARN] it logs the
 * SQL and carries on. The schema itself is never changed: migrations are a migration tool's job, such as
 * `boot-db-flyway`'s.
 *
 * It is in the [DefaultPhase.Initialization] phase, after [DbMilestones.SchemaMigrated], so it checks the schema once
 * a migration module, such as `boot-db-flyway`'s, has migrated it, if the application has one, and before the
 * application's services that use it start.
 */
class SchemaVerificationService(
  private val database: Database,
  private val tables: Set<Table>,
  private val mode: SchemaVerification = SchemaVerification.FAIL,
) : AppService {

  override fun start() {
    if (mode == SchemaVerification.OFF) return
    if (tables.isEmpty()) {
      log.debug { "No Exposed tables are registered, so there is no database schema to verify" }
      return
    }

    val statements = transaction(database) {
      MigrationUtils.statementsRequiredForDatabaseMigration(*tables.toTypedArray(), withLogs = false)
    }
    if (statements.isEmpty()) {
      log.info { "Database schema matches the Exposed definitions of ${tables.joinToString { it.tableName }}" }
      return
    }

    val exception = SchemaVerificationException(statements)
    when (mode) {
      SchemaVerification.FAIL -> throw exception
      SchemaVerification.WARN -> log.warn { exception.message }
      SchemaVerification.OFF -> Unit
    }
  }

  override fun ordering() = Ordering(DefaultPhase.Initialization, after = setOf(DbMilestones.SchemaMigrated))

  companion object {
    private val log = logger()
  }
}

/** The database schema doesn't match the Exposed tables: [statements] is the SQL that would make it match. */
class SchemaVerificationException(val statements: List<String>) : IllegalStateException(
  "The database schema does not match the Exposed table definitions. Add a migration that makes these changes, " +
    "or change the table definitions:\n" + statements.joinToString("\n") { "  $it;" }
)
