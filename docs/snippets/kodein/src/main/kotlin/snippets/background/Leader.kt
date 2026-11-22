package snippets.background

import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientEngine
import com.github.rocketraman.bootable.client.http.ktor.ktorClientModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.leader.k8s.kubernetesLeaderElectionModule
import com.github.rocketraman.bootable.periodic.periodicModule
import org.kodein.di.DI
import org.kodein.di.bindSingleton

// tag::wire[]
fun leaderDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(ktorClientModule)                   // the HTTP clients, from http.client
  bindSingleton<KtorClientEngine> { CioClientEngine() }
  import(kubernetesLeaderElectionModule)     // the LeaderElection, from leader.kubernetes, after ktorClientModule
  import(periodicModule)                     // its loops gated on that LeaderElection
  // ... and the loops
}
// end::wire[]
