package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.boot.Health
import com.github.rocketraman.bootable.boot.LifecycleState
import com.github.rocketraman.bootable.leader.LeaderElection

/**
 * Whether a [PeriodicAppService] runs this time round: it asks before each run, and skips the run when the gate is
 * closed, and asks again an interval later. It must return promptly, without blocking.
 *
 * ```
 * PeriodicGate.leader(leaderElection) and PeriodicGate.running(health)
 * ```
 */
fun interface PeriodicGate {
  fun isOpen(): Boolean

  /** Open only when both this gate and [other] are. */
  infix fun and(other: PeriodicGate): PeriodicGate = PeriodicGate { isOpen() && other.isOpen() }

  companion object {
    /** Always open: every instance runs the loop. */
    val Always: PeriodicGate = PeriodicGate { true }

    /** Open while this instance is the leader, so that only one instance runs the loop. */
    fun leader(leaderElection: LeaderElection): PeriodicGate = PeriodicGate { leaderElection.isLeader() }

    /**
     * Open while the application is [running][LifecycleState.Running]: once every service has started, and until
     * shutdown begins. A loop in the `Serving` phase, the default, starts with the last services to start, so this
     * holds its runs back only until the others in that phase, such as the HTTP server, have started too.
     */
    fun running(health: Health): PeriodicGate = PeriodicGate { health.state == LifecycleState.Running }
  }
}
