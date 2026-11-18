package com.github.rocketraman.bootable.leader.k8s

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.client.http.ktor.KtorClientDefaults
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.leader.LeaderElection
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock

/**
 * Provides the application's [LeaderElection], a [KubernetesLeaderElection] with its [KubernetesLeaderElectionConfig]
 * decoded from the `leader.kubernetes` config section, and registers it into the app-service set, so it takes part in
 * the election while the application runs. Uses the graph's `kotlin.time.Clock`, if it has one, or else
 * `Clock.System`.
 *
 * It calls the API server through the HTTP client named by [KubernetesLeaderElectionConfig.httpClient], `kubernetes`
 * by default, or else the default client, of `KtorClientBindings` (from boot-client-http-ktor-metro), which needs the
 * graph to provide a `KtorClientEngine`. In a cluster, that client trusts the API server with no TLS config of the
 * application's: see [kubernetesClientDefaults].
 *
 * `PeriodicBindings` (from boot-periodic-metro) gates the application's periodic services on it.
 */
@ContributesTo(BootScope::class)
interface KubernetesLeaderElectionBindings {
  companion object {
    @Provides @SingleIn(BootScope::class)
    fun kubernetesLeaderElectionConfig(binder: ConfigBinder): KubernetesLeaderElectionConfig =
      binder.config("leader.kubernetes")

    @Provides @SingleIn(BootScope::class)
    fun kubernetesLeaderElection(
      config: KubernetesLeaderElectionConfig,
      clients: KtorClientService,
      clock: Clock = Clock.System,
    ): KubernetesLeaderElection = KubernetesLeaderElection(config, clients.getOrDefault(config.httpClient), clock)

    @Provides fun leaderElection(election: KubernetesLeaderElection): LeaderElection = election

    @Provides @IntoSet fun kubernetesLeaderElectionAppService(election: KubernetesLeaderElection): AppService = election

    @Provides @IntoSet
    fun kubernetesClientDefaults(config: KubernetesLeaderElectionConfig): KtorClientDefaults =
      com.github.rocketraman.bootable.leader.k8s.kubernetesClientDefaults(config)
  }
}
