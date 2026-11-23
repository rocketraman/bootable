package snippets.background

import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientEngine
import com.github.rocketraman.bootable.client.http.ktor.koin.ktorClientModule
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import com.github.rocketraman.bootable.leader.k8s.koin.kubernetesLeaderElectionModule
import com.github.rocketraman.bootable.periodic.koin.periodicModule
import org.koin.dsl.koinApplication
import org.koin.dsl.module

// tag::wire[]
fun leaderKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    ktorClientModule(),                // the HTTP clients, from http.client
    module { single<KtorClientEngine> { CioClientEngine() } },
    kubernetesLeaderElectionModule(),  // the LeaderElection, from leader.kubernetes, over ktorClientModule()'s clients
    periodicModule(),                  // its loops gated on that LeaderElection
    // ... and the loops
  )
}.koin
// end::wire[]
