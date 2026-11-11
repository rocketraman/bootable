@file:JvmName("KubernetesLeaderElectionModuleInara")

package com.github.rocketraman.bootable.leader.k8s

import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.client.http.ktor.KtorClientDefaults
import com.github.rocketraman.bootable.client.http.ktor.KtorClientModule
import com.github.rocketraman.bootable.client.http.ktor.NamedClientDefaults
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.leader.LeaderElectionModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import kotlin.time.Clock

/**
 * The Inara module that provides the application's `LeaderElection`, as a [LeaderElectionModule], with a
 * [KubernetesLeaderElection], with its [KubernetesLeaderElectionConfig] decoded from the `leader.kubernetes` config
 * section. The election is one of the application's services, so it takes part in the election while the application
 * runs.
 *
 * It calls the API server through the HTTP client named by [KubernetesLeaderElectionConfig.httpClient], `kubernetes`
 * by default, or else the default client, of the [KtorClientModule] it requires. In a cluster, that client trusts the
 * API server with no TLS config of the application's: see [kubernetesClientDefaults].
 *
 * `periodicModule()` (from boot-periodic) gates the application's periodic services on it.
 */
@DIModule
interface KubernetesLeaderElectionModule : LeaderElectionModule, @DIModule.Required KtorClientModule {
  val kubernetesLeaderElectionConfig: KubernetesLeaderElectionConfig
  @AppServices val kubernetesLeaderElection: KubernetesLeaderElection
  @NamedClientDefaults val kubernetesClientDefaults: KtorClientDefaults
}

/** A [KubernetesLeaderElectionModule], whose leases are timed by [clock]. */
fun kubernetesLeaderElectionModule(clock: Clock = Clock.System): KubernetesLeaderElectionModulePart =
  KubernetesLeaderElectionModulePart(
    kubernetesLeaderElectionConfig = config("leader.kubernetes"),
    kubernetesLeaderElection = Binding.single {
      val config = kubernetesLeaderElectionConfig
      KubernetesLeaderElection(config, ktorClientService.getOrDefault(config.httpClient), clock)
    },
    leaderElection = Binding.single { kubernetesLeaderElection },
    // qualified: the kubernetesClientDefaults member shadows the top-level function
    kubernetesClientDefaults = Binding.single {
      com.github.rocketraman.bootable.leader.k8s.kubernetesClientDefaults(kubernetesLeaderElectionConfig)
    },
  )
