package com.github.rocketraman.bootable.scheduler.db

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.SchedulerBuilder
import com.github.kagkarlsson.scheduler.SchedulerName
import com.github.kagkarlsson.scheduler.jdbc.JdbcCustomization
import com.github.kagkarlsson.scheduler.serializer.Serializer
import com.github.kagkarlsson.scheduler.task.OnStartup
import com.github.kagkarlsson.scheduler.task.Task
import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.db.jdbc.DbMilestones
import javax.sql.DataSource
import kotlin.time.Clock
import kotlin.time.toJavaDuration
import kotlin.time.toJavaInstant
import com.github.kagkarlsson.scheduler.Clock as DbSchedulerClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adjusts db-scheduler's [SchedulerBuilder] after the typed settings are applied, for what [DbSchedulerConfig] doesn't
 * cover, such as a `SchedulerListener`, an `ExecutionInterceptor`, or another serializer.
 */
fun interface DbSchedulerCustomizer {
  fun customize(builder: SchedulerBuilder)
}

/**
 * Provides db-scheduler tasks, recurring and one-time, to [DbSchedulerService]: with DI, the application binds its
 * providers into a `Set<DbSchedulerTaskProvider>`.
 *
 * A provider is usually the class whose work its tasks do. It provides them all, and exposes each typed, such as the
 * `OneTimeTask<T>` that code schedules instances of, so a task needs no second binding:
 *
 * ```
 * class Reminders(private val mailer: Mailer) : DbSchedulerTaskProvider {
 *   val send: OneTimeTask<Reminder> = Tasks.oneTime("send-reminder", Reminder::class.java).execute { i, _ -> mailer.send(i.data) }
 *   val sweep: RecurringTask<Void> = Tasks.recurring("sweep-reminders", fixedDelay(1.hours)).execute { _, _ -> mailer.sweep() }
 *   override fun tasks() = listOf(send, sweep)
 * }
 * ```
 */
fun interface DbSchedulerTaskProvider {
  fun tasks(): Collection<Task<*>>
}

/**
 * Runs [db-scheduler](https://github.com/kagkarlsson/db-scheduler)'s [Scheduler] over the application's [DataSource],
 * usually the pool of `boot-db-jdbc`'s `JdbcDataSourceService`: persistent tasks, recurring and one-time, that survive
 * restarts, and that run once across all the application's instances, each execution on whichever instance locks its
 * row first. So, unlike `boot-periodic`'s in-process loops, they need no leader election.
 *
 * [tasks] are the tasks it knows: a recurring task, one that is `OnStartup`, such as `Tasks.recurring(…)`'s, is
 * scheduled when the scheduler starts, and a one-time task, such as `Tasks.oneTime(…)`'s, runs the executions the
 * application schedules through [scheduler], a `SchedulerClient`:
 *
 * ```
 * val sendReminder = Tasks.oneTime("send-reminder", Reminder::class.java).execute { instance, _ -> send(instance.data) }
 * val purge = Tasks.recurring("purge", FixedDelay.ofHours(1)).execute { _, _ -> purgeExpired() }
 * val db = DbSchedulerService(database.dataSource, binder.config("db.scheduler"), listOf(sendReminder, purge))
 * …
 * db.scheduler.schedule(sendReminder.instance(reminder.id, reminder), clock.now() + 1.days)
 * ```
 *
 * Task data is serialized with kotlinx-serialization by default, by [KotlinxSerializationTaskDataSerializer].
 *
 * It is in the [DefaultPhase.Serving] phase, as something that brings work into the application, and after
 * [DbMilestones.SchemaMigrated], so it starts once its table is migrated and the services its tasks use have
 * started, and stops before them. Shutdown waits up to [DbSchedulerConfig.shutdownMaxWait] for executing tasks.
 * Creating it opens no connection.
 *
 * Its table comes from the migrations this module ships for Flyway, in `classpath:bootable/db-scheduler/<database>`,
 * or the application's own.
 *
 * @param jdbcUrl the database's JDBC URL, from which [DbSchedulerDialect.Auto] picks the dialect; without it, the
 *   dialect is detected from the database when the service starts, or, for
 *   [DbSchedulerPollingStrategy.LockAndFetch], which needs it at once, when the service is created.
 * @param clock db-scheduler's clock, e.g. a fixed one in tests.
 */
class DbSchedulerService(
  dataSource: DataSource,
  config: DbSchedulerConfig = DbSchedulerConfig(),
  tasks: Collection<Task<*>> = emptyList(),
  customizer: DbSchedulerCustomizer = DbSchedulerCustomizer { },
  serializer: Serializer = KotlinxSerializationTaskDataSerializer(),
  clock: Clock = Clock.System,
  jdbcUrl: String? = null,
) : AppService {

  private val jdbcCustomization = jdbcCustomization(config.dialect, config.persistTimestampsInUtc, dataSource, jdbcUrl)

  /** The scheduler, and the `SchedulerClient` to schedule, reschedule, and cancel executions with. */
  val scheduler: Scheduler = schedulerBuilder(dataSource, config, tasks, serializer, clock, jdbcCustomization)
    .also(customizer::customize)
    .build()

  override suspend fun start() = withContext(Dispatchers.IO) {
    // detect the database now, if it is to be detected, while no connection is held: detecting it on first use, within
    // a statement of the scheduler's, would wait for a second connection, which a pool of one, as SQLite's, never has
    (jdbcCustomization as? LazyJdbcCustomization)?.resolve()
    scheduler.start()
  }

  // waits for the executions running to finish
  override suspend fun shutdown() = withContext(Dispatchers.IO) {
    scheduler.stop()
  }

  override fun ordering() = Ordering(DefaultPhase.Serving, after = setOf(DbMilestones.SchemaMigrated))
}

internal fun schedulerBuilder(
  dataSource: DataSource,
  config: DbSchedulerConfig,
  tasks: Collection<Task<*>>,
  serializer: Serializer,
  clock: Clock,
  jdbcCustomization: JdbcCustomization,
): SchedulerBuilder {
  val (startTasks, knownTasks) = tasks.partition { it is OnStartup }
  @Suppress("UNCHECKED_CAST")
  return Scheduler.create(dataSource, knownTasks).apply {
    // startTasks takes tasks that are both a Task and OnStartup, which Kotlin can't express as one type
    startTasks(startTasks as List<Nothing>)
    serializer(serializer)
    jdbcCustomization(jdbcCustomization)
    config.threads?.let { threads(it) }
    config.pollingInterval?.let { pollingInterval(it.toJavaDuration()) }
    when (config.pollingStrategy) {
      DbSchedulerPollingStrategy.Fetch -> pollUsingFetch(LOWER_LIMIT_FRACTION, FETCH_UPPER_LIMIT_FRACTION)
      DbSchedulerPollingStrategy.LockAndFetch -> pollUsingLockAndFetch(LOWER_LIMIT_FRACTION, LOCK_AND_FETCH_UPPER_LIMIT_FRACTION)
      null -> Unit
    }
    config.heartbeatInterval?.let { heartbeatInterval(it.toJavaDuration()) }
    config.missedHeartbeatsLimit?.let { missedHeartbeatsLimit(it) }
    config.shutdownMaxWait?.let { shutdownMaxWait(it.toJavaDuration()) }
    config.deleteUnresolvedAfter?.let { deleteUnresolvedAfter(it.toJavaDuration()) }
    config.tableName?.let { tableName(it) }
    config.schedulerName?.let { schedulerName(SchedulerName.Fixed(it)) }
    if (config.immediateExecution) enableImmediateExecution()
    if (config.priority) enablePriority()
    // db-scheduler's own clock unless another is given, as it is in tests
    if (clock !== Clock.System) clock(DbSchedulerClock { clock.now().toJavaInstant() })
  }
}

// db-scheduler's defaults for each strategy, as its README recommends them
private const val LOWER_LIMIT_FRACTION = 0.5
private const val FETCH_UPPER_LIMIT_FRACTION = 3.0
private const val LOCK_AND_FETCH_UPPER_LIMIT_FRACTION = 1.0
