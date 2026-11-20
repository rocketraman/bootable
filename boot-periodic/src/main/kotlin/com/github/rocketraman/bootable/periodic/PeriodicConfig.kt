package com.github.rocketraman.bootable.periodic

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The settings of one [PeriodicAppService], decoded from a config section of the application's choosing, one for each
 * loop. Every setting has a default, so the section is optional:
 *
 * ```
 * notifications {
 *   loop {
 *     interval = 5s
 *     maxConsecutiveFailures = 10
 *   }
 * }
 * ```
 *
 * ```
 * binder.config<PeriodicConfig>("notifications.loop")
 * ```
 */
data class PeriodicConfig(
  /** Whether the loop runs at all. A disabled loop starts nothing, and logs that it is disabled. */
  val enabled: Boolean = true,
  /**
   * How long to wait between one run's end and the next one's start: a fixed delay, so runs never overlap, and a run
   * that takes longer than the interval delays the next rather than piling up behind it.
   */
  val interval: Duration = 1.minutes,
  /** How long to wait after the service starts before the first run. None by default: the first run is at once. */
  val initialDelay: Duration = Duration.ZERO,
  /** The longest a run may take before it is cancelled, which counts as a failure. None by default. */
  val runTimeout: Duration? = null,
  /**
   * How long shutdown waits for a run in progress to finish before cancelling it. Shutdown never starts another run,
   * and doesn't wait for the interval.
   */
  val shutdownTimeout: Duration = 10.seconds,
  /**
   * How many runs in a row may fail before the loop gives up, as [onFailureLimit] says: a guard against a poison pill,
   * such as a record that fails every run, which would otherwise fail forever. A run the gate skips neither fails nor
   * succeeds. No limit by default.
   */
  val maxConsecutiveFailures: Int? = null,
  /** What to do when [maxConsecutiveFailures] runs in a row have failed. */
  val onFailureLimit: FailureLimitAction = FailureLimitAction.StopLoop,
) {
  init {
    require(interval.isPositive()) { "The interval must be positive: $interval" }
    require(!initialDelay.isNegative()) { "The initial delay must not be negative: $initialDelay" }
    require(runTimeout == null || runTimeout.isPositive()) { "The run timeout must be positive: $runTimeout" }
    require(!shutdownTimeout.isNegative()) { "The shutdown timeout must not be negative: $shutdownTimeout" }
    require(maxConsecutiveFailures == null || maxConsecutiveFailures > 0) {
      "The consecutive failure limit must be positive: $maxConsecutiveFailures"
    }
  }
}

/** What a [PeriodicAppService] does once [PeriodicConfig.maxConsecutiveFailures] runs in a row have failed. */
enum class FailureLimitAction {
  /** Stop the loop, and log an error; the rest of the application keeps running. */
  StopLoop,

  /**
   * Shut the application down, with exit code 1, as a service whose background work fails does, so that its platform,
   * such as Kubernetes, restarts it, or an operator notices.
   */
  Shutdown,
}
