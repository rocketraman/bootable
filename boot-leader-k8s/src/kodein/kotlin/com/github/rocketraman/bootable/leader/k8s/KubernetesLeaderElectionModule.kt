package com.github.rocketraman.bootable.leader.k8s

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.client.http.ktor.KtorClientDefaults
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.leader.LeaderElection
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.instanceOrNull
import org.kodein.di.singleton
import kotlin.time.Clock

/**
 * A Kodein module that binds the application's [LeaderElection] to a [KubernetesLeaderElection], with its
 * [KubernetesLeaderElectionConfig] decoded from the `leader.kubernetes` config section, and registers it into the
 * app-service set, so it takes part in the election while the application runs. Requires a `ConfigBinder` binding
 * (e.g. from boot-config-hoplite's `hopliteConfigModule`). Uses the `kotlin.time.Clock` binding, if there is one, or
 * else `Clock.System`.
 *
 * It calls the API server through the HTTP client named by [KubernetesLeaderElectionConfig.httpClient], `kubernetes`
 * by default, or else the default client, of `ktorClientModule` (from boot-client-http-ktor-kodein), which it needs, with
 * an engine module's `KtorClientEngine`; import it after `ktorClientModule`. In a cluster, that client trusts the API
 * server with no TLS config of the application's: see [kubernetesClientDefaults].
 *
 * `periodicModule` (from boot-periodic-kodein) gates the application's periodic services on it.
 */
val kubernetesLeaderElectionModule by DI.Module {
  bindSingleton<KubernetesLeaderElectionConfig> { instance<ConfigBinder>().config("leader.kubernetes") }
  bindSingleton {
    val config = instance<KubernetesLeaderElectionConfig>()
    val client = instance<KtorClientService>().getOrDefault(config.httpClient)
    KubernetesLeaderElection(config, client, instanceOrNull<Clock>() ?: Clock.System)
  }
  bindSingleton<LeaderElection> { instance<KubernetesLeaderElection>() }
  inBindSet<AppService> { add { singleton { instance<KubernetesLeaderElection>() } } }
  inBindSet<KtorClientDefaults> { add { singleton { kubernetesClientDefaults(instance()) } } }
}
