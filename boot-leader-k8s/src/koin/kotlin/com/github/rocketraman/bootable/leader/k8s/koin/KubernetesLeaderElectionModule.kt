package com.github.rocketraman.bootable.leader.k8s.koin

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.client.http.ktor.KtorClientDefaults
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.leader.LeaderElection
import com.github.rocketraman.bootable.leader.k8s.KubernetesLeaderElection
import com.github.rocketraman.bootable.leader.k8s.KubernetesLeaderElectionConfig
import com.github.rocketraman.bootable.leader.k8s.kubernetesClientDefaults
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * A Koin module that defines the application's [LeaderElection] as a [KubernetesLeaderElection], with its
 * [KubernetesLeaderElectionConfig] decoded from the `leader.kubernetes` config section, and adds it to the app
 * services, so it takes part in the election while the application runs. Requires a `ConfigBinder` definition (e.g.
 * from boot-config-hoplite's `hopliteConfigModule()`). Uses the `kotlin.time.Clock` definition, if there is one, or
 * else `Clock.System`.
 *
 * It calls the API server through the HTTP client named by [KubernetesLeaderElectionConfig.httpClient], `kubernetes`
 * by default, or else the default client, of `ktorClientModule()` (from boot-client-http-ktor-koin), which it needs,
 * with an engine module's `KtorClientEngine`. In a cluster, that client trusts the API server with no TLS config of the
 * application's: see [kubernetesClientDefaults].
 *
 * `periodicModule()` (from boot-periodic-koin) gates the application's periodic services on it.
 */
fun kubernetesLeaderElectionModule(): Module = module {
  single<KubernetesLeaderElectionConfig> { get<ConfigBinder>().config("leader.kubernetes") }
  single {
    val config = get<KubernetesLeaderElectionConfig>()
    val client = get<KtorClientService>().getOrDefault(config.httpClient)
    KubernetesLeaderElection(config, client, getOrNull<Clock>() ?: Clock.System)
  }
  single<LeaderElection> { get<KubernetesLeaderElection>() }
  single<AppService>(named<KubernetesLeaderElection>()) { get<KubernetesLeaderElection>() }
  single<KtorClientDefaults>(named<KubernetesLeaderElection>()) { kubernetesClientDefaults(get()) }
}
