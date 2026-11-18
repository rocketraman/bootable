package com.github.rocketraman.bootable.scheduler.db

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.rocketraman.bootable.boot.HealthCheck
import com.github.rocketraman.bootable.boot.HealthStatus

/**
 * A readiness [HealthCheck] that [scheduler] is running: started, and neither paused nor shutting down. It doesn't
 * query the database: combine it with `boot-db-jdbc`'s `JdbcHealthCheck` for that.
 *
 * Opt in to it: readiness that depends on the scheduler takes the instance out of rotation when it isn't running,
 * which helps only when the application's requests depend on its tasks running.
 */
class DbSchedulerHealthCheck(private val scheduler: Scheduler) : HealthCheck {
  override fun name() = "db-scheduler"

  override fun check(): HealthStatus {
    val state = scheduler.schedulerState
    return when {
      !state.isStarted -> HealthStatus.Unhealthy("not started")
      state.isShuttingDown -> HealthStatus.Unhealthy("shutting down")
      state.isPaused -> HealthStatus.Unhealthy("paused")
      else -> HealthStatus.Healthy
    }
  }
}
