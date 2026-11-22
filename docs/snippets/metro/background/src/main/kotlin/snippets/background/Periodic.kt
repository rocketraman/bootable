package snippets.background

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.periodic.PeriodicServices
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

// tag::wire[]
@ContributesTo(BackgroundScope::class)
interface NotificationBindings {
  @Provides @SingleIn(BackgroundScope::class)
  fun notifications(): Notifications = Notifications()

  // PeriodicServices is gated on the graph's LeaderElection, if it has one
  @Provides @IntoSet
  fun notificationsLoop(periodic: PeriodicServices, binder: ConfigBinder, notifications: Notifications): AppService =
    periodic.create("notifications", binder.config("notifications.loop")) { notifications.sendPending() }
}
// end::wire[]
