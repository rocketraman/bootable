package snippets.background

import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerTaskProvider
import com.github.rocketraman.bootable.scheduler.db.fixedDelay
import com.github.rocketraman.bootable.scheduler.db.schedule
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/** The application's own work, which the loop and the tasks do, as in the vanilla snippets. */
class Notifications {
  /** Sends the notifications that are pending. */
  suspend fun sendPending() {
    // ...
  }

  /** Sends the reminder with the id [reminderId]. */
  suspend fun sendReminder(reminderId: String) {
    // ...
  }

  /** Deletes the reminders that have been sent. */
  fun purgeSent() {
    // ...
  }
}

class Reminders(private val notifications: Notifications) : DbSchedulerTaskProvider {
  // one-time: an execution for each reminder, whose instance id is the reminder's id
  val send: OneTimeTask<Void> = Tasks.oneTime("send-reminder").execute { instance, _ ->
    runBlocking { notifications.sendReminder(instance.id) }  // db-scheduler runs tasks on threads of its own
  }

  // recurring: scheduled when the scheduler starts, then every hour
  val purge: RecurringTask<Void> = Tasks.recurring("purge-reminders", fixedDelay(1.hours)).execute { _, _ ->
    notifications.purgeSent()
  }

  override fun tasks() = listOf(send, purge)
}

class ReminderApi(
  private val reminders: Reminders,
  private val scheduler: SchedulerClient,
  private val clock: Clock = Clock.System,
) {
  /** Sends reminder [id] in an hour, from whichever instance locks its execution first. */
  fun remindInAnHour(id: String) {
    scheduler.schedule(reminders.send.instance(id), clock.now() + 1.hours)
  }
}
