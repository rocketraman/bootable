package com.github.rocketraman.bootable.leader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Whether this instance is the leader of the instances of the application, such as the replicas of a Kubernetes
 * deployment: at most one of them is, at any time.
 *
 * Use it for work that only one instance should do, such as a periodic loop that sends pending notifications. Work
 * that a database already coordinates, such as `boot-scheduler-db`'s tasks, which db-scheduler runs on one instance
 * each by locking their rows, needs no leader.
 *
 * An implementation that elects a leader among instances, such as by a lease, guarantees it by stepping down before
 * another instance can take over: [leadership] turns false a margin before then. Work that only the leader does must
 * stop within that margin once it does, as `boot-periodic`'s leader gate does, cancelling a run in progress.
 *
 * The guarantee holds as long as this instance doesn't pause, such as for a long garbage collection, or a suspended
 * VM, for longer than that margin, since it can't step down while paused: it would then carry on as though it still led.
 * The [fencingToken] closes that gap where the work writes. A term is one unbroken stretch of one instance's
 * leadership, from when it starts leading until it stops; each term has a token greater than every
 * earlier term's, which the work sends with its writes, and the store refuses a write with a token lower than one it has
 * seen, such as by a database `UPDATE ... WHERE fence <= :token`, so a paused leader's late writes fail once a later
 * leader has written.
 *
 * Implementations: [AlwaysLeader], for an application with one instance, or for running locally, and
 * `KubernetesLeaderElection` from `boot-leader-k8s`, which holds a Kubernetes Lease.
 */
interface LeaderElection {
  /** Whether this instance is the leader, as it changes. */
  val leadership: StateFlow<Boolean>

  /**
   * The fencing token of this instance's current term as leader, or null while it doesn't lead.
   *
   * It is the same throughout a term, and greater in each later term, of whichever instance: read it as the work
   * starts, and send it with each write, for the store to refuse a write from an earlier term once it has seen a later
   * one. [requireFencingToken] reads it, or throws if this instance doesn't lead.
   */
  val fencingToken: Long?

  /** Whether this instance is the leader now. */
  fun isLeader(): Boolean = leadership.value
}

/**
 * The [fencing token][LeaderElection.fencingToken] of this instance's current term as leader, or throws a
 * [NotLeaderException] if it doesn't lead: for work that only the leader does, as it starts.
 */
fun LeaderElection.requireFencingToken(): Long = fencingToken ?: throw NotLeaderException("$this doesn't lead")

/** Work that only the leader does found that this instance doesn't lead. */
class NotLeaderException(message: String) : IllegalStateException(message)

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

  /** Always the same term, since nothing else ever leads. */
  override val fencingToken: Long = 0

  override fun toString() = "AlwaysLeader"
}
