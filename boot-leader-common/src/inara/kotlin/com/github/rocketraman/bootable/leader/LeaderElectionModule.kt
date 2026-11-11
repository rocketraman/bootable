@file:JvmName("LeaderElectionModuleInara")

package com.github.rocketraman.bootable.leader

import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that provides the application's [LeaderElection], for the modules that run only on the leader.
 * Compose `kubernetesLeaderElectionModule()` from boot-leader-k8s, which elects one instance, or, for an application
 * whose every instance leads, the [AlwaysLeaderModule].
 */
@DIModule
interface LeaderElectionModule {
  val leaderElection: LeaderElection
}

/** A [LeaderElectionModule] whose every instance is the leader. */
val AlwaysLeaderModule: LeaderElectionModule = LeaderElectionModule(leaderElection = Binding.value(AlwaysLeader))
