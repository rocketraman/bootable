package com.github.rocketraman.bootable.scheduler.db

import com.github.kagkarlsson.scheduler.jdbc.AutodetectJdbcCustomization
import com.github.kagkarlsson.scheduler.jdbc.DefaultJdbcCustomization
import com.github.kagkarlsson.scheduler.jdbc.JdbcCustomization
import com.github.kagkarlsson.scheduler.jdbc.JdbcTaskRepositoryContext
import com.github.kagkarlsson.scheduler.jdbc.MariaDBJdbcCustomization
import com.github.kagkarlsson.scheduler.jdbc.MssqlJdbcCustomization
import com.github.kagkarlsson.scheduler.jdbc.MySQL8JdbcCustomization
import com.github.kagkarlsson.scheduler.jdbc.MySQLJdbcCustomization
import com.github.kagkarlsson.scheduler.jdbc.OracleJdbcCustomization
import com.github.kagkarlsson.scheduler.jdbc.PostgreSqlJdbcCustomization
import com.github.kagkarlsson.scheduler.jdbc.SqliteJdbcCustomization
import com.github.kagkarlsson.scheduler.task.Execution
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import javax.sql.DataSource

/** The dialect of the database at [jdbcUrl], by its JDBC subprotocol, or null if it isn't one db-scheduler knows. */
internal fun dialectOf(jdbcUrl: String): DbSchedulerDialect? = when (jdbcUrl.removePrefix("jdbc:").substringBefore(':')) {
  "postgresql" -> DbSchedulerDialect.PostgreSql
  // MySQL before 8 is past its end of life
  "mysql" -> DbSchedulerDialect.MySql8
  "mariadb" -> DbSchedulerDialect.MariaDb
  "sqlserver" -> DbSchedulerDialect.SqlServer
  "oracle" -> DbSchedulerDialect.Oracle
  "sqlite" -> DbSchedulerDialect.Sqlite
  else -> null
}

/** db-scheduler's `JdbcCustomization` for [dialect], resolving [DbSchedulerDialect.Auto] by [jdbcUrl] if given. */
internal fun jdbcCustomization(
  dialect: DbSchedulerDialect,
  utc: Boolean,
  dataSource: DataSource,
  jdbcUrl: String?,
): JdbcCustomization = when (dialect) {
  DbSchedulerDialect.Auto -> jdbcUrl?.let(::dialectOf)?.let { jdbcCustomization(it, utc, dataSource, null) }
    ?: LazyJdbcCustomization { AutodetectJdbcCustomization(dataSource, utc) }
  DbSchedulerDialect.PostgreSql -> PostgreSqlJdbcCustomization(false, utc)
  DbSchedulerDialect.MySql -> MySQLJdbcCustomization(utc)
  DbSchedulerDialect.MySql8 -> MySQL8JdbcCustomization(utc)
  DbSchedulerDialect.MariaDb -> MariaDBJdbcCustomization(utc)
  DbSchedulerDialect.SqlServer -> MssqlJdbcCustomization(utc)
  DbSchedulerDialect.Oracle -> OracleJdbcCustomization(utc)
  // SQLite's is always UTC
  DbSchedulerDialect.Sqlite -> SqliteJdbcCustomization()
  DbSchedulerDialect.Generic -> DefaultJdbcCustomization(utc)
}

/**
 * A `JdbcCustomization` created when first used, or [resolve]d, so that detecting the database, which takes a
 * connection, waits for the scheduler's start, after the pool's, rather than happening when the service is created.
 */
internal class LazyJdbcCustomization(create: () -> JdbcCustomization) : JdbcCustomization {
  private val delegate by lazy(create)

  /** Creates the customization now, if it hasn't been. */
  fun resolve() {
    delegate
  }

  override fun getName(): String = delegate.name
  override fun setInstant(p: PreparedStatement, index: Int, value: Instant?) = delegate.setInstant(p, index, value)
  override fun getInstant(rs: ResultSet, columnName: String): Instant? = delegate.getInstant(rs, columnName)
  override fun setTaskData(p: PreparedStatement, index: Int, value: ByteArray?) = delegate.setTaskData(p, index, value)
  override fun getTaskData(rs: ResultSet, columnName: String): ByteArray? = delegate.getTaskData(rs, columnName)
  override fun supportsExplicitQueryLimitPart() = delegate.supportsExplicitQueryLimitPart()
  override fun getQueryLimitPart(limit: Int): String = delegate.getQueryLimitPart(limit)
  override fun supportsSingleStatementLockAndFetch() = delegate.supportsSingleStatementLockAndFetch()
  override fun lockAndFetchSingleStatement(
    ctx: JdbcTaskRepositoryContext,
    now: Instant,
    limit: Int,
    orderByPriority: Boolean,
  ): List<Execution> = delegate.lockAndFetchSingleStatement(ctx, now, limit, orderByPriority)
  override fun supportsGenericLockAndFetch() = delegate.supportsGenericLockAndFetch()
  override fun createGenericSelectForUpdateQuery(
    tableName: String,
    limit: Int,
    requiredAndCondition: String?,
    orderByPriority: Boolean,
  ): String = delegate.createGenericSelectForUpdateQuery(tableName, limit, requiredAndCondition, orderByPriority)
  override fun createSelectDueQuery(
    tableName: String,
    limit: Int,
    andCondition: String?,
    orderByPriority: Boolean,
  ): String = delegate.createSelectDueQuery(tableName, limit, andCondition, orderByPriority)
}
