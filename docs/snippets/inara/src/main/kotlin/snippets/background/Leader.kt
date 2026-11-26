package snippets.background

import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientModule
import com.github.rocketraman.bootable.client.http.ktor.ktorClientModule
import com.github.rocketraman.bootable.clock.ClockModule
import com.github.rocketraman.bootable.clock.clockModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.leader.k8s.KubernetesLeaderElectionModule
import com.github.rocketraman.bootable.leader.k8s.kubernetesLeaderElectionModule
import com.github.rocketraman.bootable.periodic.PeriodicModule
import com.github.rocketraman.bootable.periodic.periodicModule
import com.github.rocketraman.bootable.telemetry.otel.NoopOpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryApiModule
import org.kodein.di.inara.DIModule

// tag::wire[]
@DIModule(composed = true)
interface LeaderAppModule :
  ConfigModule, BootableModule, ClockModule, KtorClientModule, KubernetesLeaderElectionModule, OpenTelemetryApiModule,
  PeriodicModule {
  // ... and the loops
}

fun leaderAppModule() = LeaderAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  clockModule = clockModule(),
  ktorClientModule = ktorClientModule(CioClientEngine()),  // the HTTP clients, from http.client, on that engine
  // the LeaderElectionModule, from leader.kubernetes; it requires the KtorClientModule, to call the API server
  kubernetesLeaderElectionModule = kubernetesLeaderElectionModule(),
  openTelemetryApiModule = NoopOpenTelemetryModule,        // required by periodicModule: no tracing of the runs
  periodicModule = periodicModule(),                       // its loops gated on that leaderElection
)
// end::wire[]
