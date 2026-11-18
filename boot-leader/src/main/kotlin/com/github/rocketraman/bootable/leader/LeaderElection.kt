package com.github.rocketraman.bootable.leader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Whether this instance is the leader of the instances of the application, such as the replicas of a Kubernetes
 * deployment: at most one of them is, at any time, as far as the implementation can tell.
 *
 * Use it for work that only one instance should do, such as a periodic loop that sends pending notifications.
 *
 * Leadership can be lost at any time, such as when this instance can't renew it, and an implementation can only tell
 * once it has been: work that must never run on two instances at once needs a lock or a fencing token as well, such as
 * a row lock or a version check in the database.
 *
 * Implementations: [AlwaysLeader], for an application with one instance, or for running locally, and
 * `KubernetesLeaderElection` from `boot-leader-k8s`, which holds a Kubernetes Lease.
 */
interface LeaderElection {
  /** Whether this instance is the leader, as it changes. */
  val leadership: StateFlow<Boolean>

  /** Whether this instance is the leader now. */
  fun isLeader(): Boolean = leadership.value
}

/** Suspends until this instance is the leader. */
suspend fun LeaderElection.awaitLeadership() {
  leadership.first { it }
}

/**
 * A [LeaderElection] in which this instance is always the leader: for an application that runs as one instance, and
 * for running locally.
 */
object AlwaysLeader : LeaderElection {
  override val leadership: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

  override fun toString() = "AlwaysLeader"
}
