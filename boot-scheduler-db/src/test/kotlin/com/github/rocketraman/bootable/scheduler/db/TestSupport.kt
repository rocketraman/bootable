package com.github.rocketraman.bootable.scheduler.db

import com.github.kagkarlsson.scheduler.task.Task
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask
import com.github.kagkarlsson.scheduler.task.helper.RecurringTaskWithPersistentSchedule
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.rocketraman.bootable.db.flyway.FlywayConfig
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.jdbc.HikariConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.JdbcConfig
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@Serializable
data class Reminder(val user: String, val text: String)

@Serializable
sealed interface Notice {
  @Serializable
  @SerialName("email")
  data class Email(val to: String) : Notice

  @Serializable
  @SerialName("sms")
  data class Sms(val number: String) : Notice
}

@Serializable
data class Report(override val recurrence: SerializableSchedule, val reportId: String) : PersistentScheduleData

/** Fast polling, so the tests take a moment. */
val testSchedulerConfig = DbSchedulerConfig(pollingInterval = 100.milliseconds, immediateExecution = true, shutdownMaxWait = 5.seconds)

/** What the test tasks did. */
class TaskRecord : DbSchedulerTaskProvider {
  val recurringRuns = AtomicInteger()
  val reminders: MutableList<Reminder> = CopyOnWriteArrayList()
  val notices: MutableList<Notice> = CopyOnWriteArrayList()
  val reports: MutableMap<String, Int> = ConcurrentHashMap()

  val recurring: RecurringTask<Void> = Tasks.recurring("heartbeat", fixedDelay(100.milliseconds))
    .execute { _, _ -> recurringRuns.incrementAndGet() }

  val reminder: OneTimeTask<Reminder> = Tasks.oneTime("reminder", Reminder::class.java)
    .execute { instance, _ -> reminders += instance.data }

  val notice: OneTimeTask<Notice> = Tasks.oneTime("notice", Notice::class.java)
    .execute { instance, _ -> notices += instance.data }

  val report: RecurringTaskWithPersistentSchedule<Report> = Tasks.recurringWithPersistentSchedule("report", Report::class.java)
    .execute { instance, _ -> reports.merge(instance.data.reportId, 1, Int::plus) }

  val tasks: List<Task<*>> = listOf(recurring, reminder, notice, report)

  override fun tasks() = tasks
}

fun eventually(timeout: Duration = 10.seconds, condition: () -> Boolean) {
  val start = TimeSource.Monotonic.markNow()
  while (!condition()) {
    check(start.elapsedNow() < timeout) { "Condition not met within $timeout" }
    Thread.sleep(20)
  }
}

@OptIn(ExperimentalPathApi::class)
suspend fun withTempDir(block: suspend (Path) -> Unit) {
  val dir = createTempDirectory("boot-scheduler-db")
  try {
    block(dir)
  } finally {
    dir.deleteRecursively()
  }
}

/** A started pool for [config], with db-scheduler's table migrated from this module's migrations for [database]. */
suspend fun withMigratedDatabase(config: JdbcConfig, database: String, block: suspend (DataSource) -> Unit) {
  val pool = JdbcDataSourceService(config, HikariConnectionPoolFactory())
  pool.start()
  try {
    FlywayMigrationService(pool.dataSource, FlywayConfig(locations = listOf("classpath:bootable/db-scheduler/$database"))).start()
    block(pool.dataSource)
  } finally {
    pool.shutdown()
  }
}

/**
 * Runs the scheduler over [dataSource], with a recurring task, two one-time tasks, one with a sealed type's data, and a
 * recurring task with a persistent schedule, and checks each runs, with its data read back as it was written.
 */
suspend fun checkTasksRun(dataSource: DataSource, jdbcUrl: String? = null, config: DbSchedulerConfig = testSchedulerConfig) {
  val record = TaskRecord()
  val service = DbSchedulerService(dataSource, config, record.tasks, jdbcUrl = jdbcUrl)
  service.start()
  try {
    val now = kotlin.time.Clock.System.now()
    service.scheduler.schedule(record.reminder.instance("r1", Reminder("ada", "hello")), now)
    service.scheduler.schedule(record.notice.instance("n1", Notice.Sms("555")), now)
    service.scheduler.schedule(
      record.report.schedulableInstance("weekly", Report(SerializableSchedule.FixedDelay(100.milliseconds), "weekly"))
    )
    eventually { record.recurringRuns.get() >= 2 }
    eventually { record.reminders == listOf(Reminder("ada", "hello")) }
    eventually { record.notices == listOf(Notice.Sms("555")) }
    eventually { (record.reports["weekly"] ?: 0) >= 2 }
    // a one-time execution is deleted once it has run; the recurring ones stay
    eventually { dataSource.taskNames() == listOf("heartbeat", "report") }
  } finally {
    service.shutdown()
  }
}

/** The task names of the executions in db-scheduler's table. */
fun DataSource.taskNames(): List<String> = connection.use { connection ->
  connection.createStatement().use { statement ->
    statement.executeQuery("SELECT task_name FROM scheduled_tasks ORDER BY task_name").use { rs ->
      buildList { while (rs.next()) add(rs.getString(1)) }
    }
  }
}
