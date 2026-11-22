package snippets.background

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.withHealthLifecycleController
import com.github.rocketraman.bootable.boot.defaultLifecycleControllers
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.leader.LeaderElection
import com.github.rocketraman.bootable.periodic.PeriodicAppService
import com.github.rocketraman.bootable.periodic.PeriodicFailureHandler
import com.github.rocketraman.bootable.periodic.PeriodicGate
import com.github.rocketraman.bootable.periodic.PeriodicServices
import kotlinx.coroutines.Dispatchers
import org.apache.logging.log4j.kotlin.logger

fun notificationsLoop(binder: ConfigBinder, leaderElection: LeaderElection, notifications: Notifications) =
  // tag::loop[]
  PeriodicAppService(
    "notifications",                      // its name, in its log messages and its trace
    binder.config("notifications.loop"),  // its PeriodicConfig
    PeriodicGate.leader(leaderElection),  // on the leader only
  ) {
    notifications.sendPending()
  }
  // end::loop[]

fun gatedLoop(binder: ConfigBinder, leaderElection: LeaderElection, health: HealthLifecycleController, notifications: Notifications) =
  // tag::gate[]
  PeriodicAppService(
    "notifications",
    binder.config("notifications.loop"),
    PeriodicGate.leader(leaderElection) and PeriodicGate.running(health),
  ) {
    notifications.sendPending()
  }
  // end::gate[]

private val log = logger("snippets.background.Periodic")

fun handledLoop(binder: ConfigBinder, notifications: Notifications) =
  // tag::failure[]
  PeriodicAppService(
    "notifications",
    binder.config("notifications.loop"),
    onFailure = PeriodicFailureHandler { service, failure, consecutiveFailures ->
      // in place of the default, which logs each failure as an error
      if (consecutiveFailures < 3) log.warn(failure) { "$service failed" }
      else log.error(failure) { "$service failed $consecutiveFailures times in a row" }
    },
  ) {
    notifications.sendPending()
  }
  // end::failure[]

fun blockingLoop(binder: ConfigBinder, purge: () -> Unit) =
  // tag::dispatcher[]
  PeriodicAppService("purge", binder.config("purge.loop"), dispatcher = Dispatchers.IO) {
    purge()  // JDBC calls, which block
  }
  // end::dispatcher[]

fun periodicApp(binder: ConfigBinder): Bootable {
  val notifications = Notifications()
  // tag::wire[]
  val health = HealthLifecycleController()
  // what the application's loops share: here, their gate, so they run only once the application is running
  val periodic = PeriodicServices(gate = PeriodicGate.running(health))

  return Bootable(
    setOf(
      periodic.create("notifications", binder.config("notifications.loop")) { notifications.sendPending() },
      // ... and the services the loops use
    ),
    defaultLifecycleControllers().withHealthLifecycleController(health),
  )
  // end::wire[]
}
