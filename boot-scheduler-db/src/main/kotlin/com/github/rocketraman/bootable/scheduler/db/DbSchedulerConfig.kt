package com.github.rocketraman.bootable.scheduler.db

import kotlin.time.Duration

/**
 * db-scheduler settings. Every field is optional; an unset field keeps db-scheduler's default.
 *
 * Decoded from the `db.scheduler` config section:
 *
 * ```
 * db {
 *   jdbc { ... }
 *   scheduler {
 *     threads = 5
 *     pollingInterval = 5s
 *     shutdownMaxWait = 20s
 *   }
 * }
 * ```
 *
 * For a db-scheduler setting not listed here, such as a `SchedulerListener` or an `ExecutionInterceptor`, use a
 * [DbSchedulerCustomizer].
 */
data class DbSchedulerConfig(
  /** How many tasks may execute at once, on this instance. db-scheduler's default is 10. */
  val threads: Int? = null,
  /** How often to look for due executions. db-scheduler's default is 10 seconds. */
  val pollingInterval: Duration? = null,
  /** How to look for due executions. db-scheduler's default is [DbSchedulerPollingStrategy.Fetch]. */
  val pollingStrategy: DbSchedulerPollingStrategy? = null,
  /** How often an executing task's heartbeat is updated. db-scheduler's default is 5 minutes. */
  val heartbeatInterval: Duration? = null,
  /** How many heartbeats an execution may miss before it is considered dead. db-scheduler's default is 6. */
  val missedHeartbeatsLimit: Int? = null,
  /**
   * How long shutdown waits for executing tasks to finish. db-scheduler's default is 30 minutes, longer than most
   * platforms wait for a process to stop, such as Kubernetes's 30 second grace period: set it to fit.
   */
  val shutdownMaxWait: Duration? = null,
  /** How long to keep an execution of a task this application doesn't know before deleting it. 14 days by default. */
  val deleteUnresolvedAfter: Duration? = null,
  /** The table db-scheduler keeps its executions in. db-scheduler's default is `scheduled_tasks`. */
  val tableName: String? = null,
  /** This instance's name, in the table's `picked_by` column. db-scheduler's default is the host name. */
  val schedulerName: String? = null,
  /** Whether an execution scheduled for now runs at once, rather than at the next poll. */
  val immediateExecution: Boolean = false,
  /** Whether executions run in order of their priority. It needs the `priority` column, which the migrations add. */
  val priority: Boolean = false,
  /** The SQL dialect: [DbSchedulerDialect.Auto], the default, matches the database. */
  val dialect: DbSchedulerDialect = DbSchedulerDialect.Auto,
  /**
   * Whether timestamps are written in UTC. On by default, as db-scheduler recommends, for the databases whose
   * timestamp columns have no time zone, such as MySQL's.
   */
  val persistTimestampsInUtc: Boolean = true,
)

/** How db-scheduler looks for due executions. */
enum class DbSchedulerPollingStrategy {
  /** Fetch the due executions, then lock each as it runs: works on every database. */
  Fetch,

  /**
   * Fetch and lock the due executions in one statement, `SELECT … FOR UPDATE SKIP LOCKED`: fewer statements, for
   * PostgreSQL, MySQL 8, and the other databases that support it.
   */
  LockAndFetch,
}

/** The SQL dialect db-scheduler speaks, as its `JdbcCustomization`. */
enum class DbSchedulerDialect {
  /**
   * The database's own: from the configured JDBC URL when the service is given one, as the DI flavors do, or else
   * detected from the database itself, when the scheduler starts.
   */
  Auto,
  PostgreSql,
  MySql,
  MySql8,
  MariaDb,
  SqlServer,
  Oracle,
  Sqlite,

  /** Standard SQL, for a database db-scheduler has no dialect for. */
  Generic,
}
