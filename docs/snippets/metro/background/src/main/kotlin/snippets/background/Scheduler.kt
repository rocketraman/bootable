package snippets.background

import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerTaskProvider
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

// tag::wire[]
@ContributesTo(BackgroundScope::class)
interface ReminderBindings {
  @Provides @SingleIn(BackgroundScope::class)
  fun reminders(notifications: Notifications): Reminders = Reminders(notifications)

  @Provides @IntoSet
  fun reminderTasks(reminders: Reminders): DbSchedulerTaskProvider = reminders  // its tasks, to the scheduler

  @Provides @SingleIn(BackgroundScope::class)
  fun reminderApi(reminders: Reminders, scheduler: SchedulerClient): ReminderApi = ReminderApi(reminders, scheduler)
  // ... and the services that use ReminderApi
}
// end::wire[]
