package snippets.background

import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.leader.AlwaysLeaderModule
import com.github.rocketraman.bootable.leader.LeaderElectionModule
import com.github.rocketraman.bootable.periodic.PeriodicAppService
import com.github.rocketraman.bootable.periodic.PeriodicModule
import com.github.rocketraman.bootable.periodic.periodicModule
import com.github.rocketraman.bootable.telemetry.otel.NoopOpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryApiModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

// tag::wire[]
@DIModule(composed = true)
interface PeriodicAppModule :
  ConfigModule, BootableModule, LeaderElectionModule, OpenTelemetryApiModule, PeriodicModule {
  val notifications: Notifications
  @AppServices val notificationsLoop: PeriodicAppService
}

fun periodicAppModule() = PeriodicAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  leaderElectionModule = AlwaysLeaderModule,         // required: every instance runs the loops
  openTelemetryApiModule = NoopOpenTelemetryModule,  // required: no tracing of the runs
  periodicModule = periodicModule(),                 // periodicServices, gated on the leaderElection
  notifications = Binding.single { Notifications() },
  notificationsLoop = Binding.single {
    periodicServices.create("notifications", configBinder.config("notifications.loop")) { notifications.sendPending() }
  },
)
// end::wire[]
