package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.LifecycleState
import com.github.rocketraman.bootable.leader.LeaderElection
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge

/**
 * Whether a [PeriodicAppService] runs this time round: it asks before each run, and skips the run when the gate is
 * closed, and asks again an interval later. It must return promptly, without blocking.
 *
 * A gate that can close while a run is in progress, such as [leader], says so through [awaitClosed], and the service
 * cancels the run when it does.
 *
 * ```
 * PeriodicGate.leader(leaderElection) and PeriodicGate.running(health)
 * ```
 */
fun interface PeriodicGate {
  fun isOpen(): Boolean

  /**
   * Suspends until the gate closes, while a run is in progress, so that the run is cancelled then. By default, it
   * never returns: the gate is only asked between runs.
   */
  suspend fun awaitClosed(): Unit = awaitCancellation()

  /** Open only when both this gate and [other] are; closing, during a run, when either does. */
  infix fun and(other: PeriodicGate): PeriodicGate {
    val first = this
    return object : PeriodicGate {
      override fun isOpen() = first.isOpen() && other.isOpen()

      override suspend fun awaitClosed() {
        merge(flow { emit(first.awaitClosed()) }, flow { emit(other.awaitClosed()) }).first()
      }
    }
  }

  companion object {
    /** Always open: every instance runs the loop. */
    val Always: PeriodicGate = PeriodicGate { true }

    /**
     * Open while this instance is the leader, so that only one instance runs the loop. A run in progress is cancelled
     * as soon as this instance stops leading.
     */
    fun leader(leaderElection: LeaderElection): PeriodicGate = object : PeriodicGate {
      override fun isOpen() = leaderElection.isLeader()

      override suspend fun awaitClosed() {
        leaderElection.leadership.first { !it }
      }

      override fun toString() = "PeriodicGate.leader($leaderElection)"
    }

    /**
     * Open while the application is [running][LifecycleState.Running]: once every service has started, and until
     * shutdown begins. A loop in the `Serving` phase, the default, starts with the last services to start, so this
     * holds its runs back only until the others in that phase, such as the HTTP server, have started too.
     */
    fun running(health: HealthLifecycleController): PeriodicGate =
      PeriodicGate { health.state == LifecycleState.Running }
  }
}
