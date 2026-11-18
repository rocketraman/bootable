package com.github.rocketraman.bootable.scheduler.db

import com.github.kagkarlsson.scheduler.task.helper.ScheduleAndData
import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule
import com.github.kagkarlsson.scheduler.task.schedule.CronStyle
import com.github.kagkarlsson.scheduler.task.schedule.Daily
import com.github.kagkarlsson.scheduler.task.schedule.Schedule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay as DbSchedulerFixedDelay

/**
 * A db-scheduler [Schedule] that can be stored with a task's data, for a recurring task whose schedule each instance
 * of it chooses at runtime, rather than one fixed when the scheduler is created: `Tasks.recurringWithPersistentSchedule`.
 * See [PersistentScheduleData].
 */
@Serializable
sealed interface SerializableSchedule {
  /** The db-scheduler schedule. */
  fun toSchedule(): Schedule

  /** A cron expression, in [zone], a time zone ID such as `Europe/Paris`, of [style]. */
  @Serializable
  @SerialName("cron")
  data class Cron(
    val expression: String,
    val zone: String = "UTC",
    val style: CronFormat = CronFormat.Spring53,
  ) : SerializableSchedule {
    override fun toSchedule(): Schedule = CronSchedule(expression, ZoneId.of(zone), style.style)
  }

  /** Every [interval], from the end of the last execution. */
  @Serializable
  @SerialName("fixedDelay")
  data class FixedDelay(val interval: Duration) : SerializableSchedule {
    override fun toSchedule(): Schedule = DbSchedulerFixedDelay.of(interval.toJavaDuration())
  }

  /** Every day, at each of [times], each `HH:mm` or `HH:mm:ss`, in [zone], a time zone ID such as `Europe/Paris`. */
  @Serializable
  @SerialName("daily")
  data class DailyAt(val times: List<String>, val zone: String = "UTC") : SerializableSchedule {
    override fun toSchedule(): Schedule = Daily(ZoneId.of(zone), times.map(LocalTime::parse))
  }
}

/** The cron expression formats db-scheduler reads: its `CronStyle`s. */
enum class CronFormat(internal val style: CronStyle) {
  /** Spring's, before 5.3: six fields, from seconds. */
  Spring(CronStyle.SPRING),

  /** Spring 5.3's: six fields, from seconds, with macros such as `@daily`, and `L` and `W`. */
  Spring53(CronStyle.SPRING53),

  /** Unix's: five fields, from minutes. */
  Unix(CronStyle.UNIX),
  Quartz(CronStyle.QUARTZ),
  Cron4j(CronStyle.CRON4J),
}

/**
 * The data of a recurring task with a persistent schedule, for `Tasks.recurringWithPersistentSchedule`: a
 * `@Serializable` class of the application's that holds the task's [recurrence] and whatever else the execution needs.
 *
 * ```
 * @Serializable
 * data class Report(override val recurrence: SerializableSchedule, val reportId: String) : PersistentScheduleData
 *
 * val reports = Tasks.recurringWithPersistentSchedule("report", Report::class.java).execute { instance, _ -> … }
 * scheduler.schedule(reports.schedulableInstance(id, Report(SerializableSchedule.DailyAt(listOf("08:00")), id)))
 * ```
 */
interface PersistentScheduleData : ScheduleAndData {
  val recurrence: SerializableSchedule

  override fun getSchedule(): Schedule = recurrence.toSchedule()

  override fun getData(): Any? = this
}
