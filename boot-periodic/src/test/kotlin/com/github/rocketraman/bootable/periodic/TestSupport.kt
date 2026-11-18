package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.boot.AdvancedAppService
import com.github.rocketraman.bootable.boot.AppService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Waits, in real time, until [condition] holds, or fails after [timeout]. The loops run on real dispatchers, while
 * TestBalloon runs a test's own coroutine on virtual time, where `delay` waits for nothing.
 */
internal fun eventually(timeout: Duration = 5.seconds, condition: () -> Boolean) {
  val start = TimeSource.Monotonic.markNow()
  while (!condition()) {
    check(start.elapsedNow() < timeout) { "Condition not met within $timeout" }
    Thread.sleep(5)
  }
}

/** Sleeps, in real time, for [duration]. */
internal fun sleep(duration: Duration) = Thread.sleep(duration.inWholeMilliseconds)

/** Starts this service, as Bootable does, with [die] as its `die` callback. */
internal fun AppService.startService(die: () -> Unit = { error("The service died") }) = when (this) {
  is AdvancedAppService -> start(die)
  else -> start()
}

/** A loop config for tests: a short interval, and a short shutdown timeout. */
internal fun testConfig(
  interval: Duration = 10.milliseconds,
  shutdownTimeout: Duration = 2.seconds,
  maxConsecutiveFailures: Int? = null,
  onFailureLimit: FailureLimitAction = FailureLimitAction.StopLoop,
  runTimeout: Duration? = null,
  enabled: Boolean = true,
) = PeriodicConfig(
  enabled = enabled,
  interval = interval,
  shutdownTimeout = shutdownTimeout,
  maxConsecutiveFailures = maxConsecutiveFailures,
  onFailureLimit = onFailureLimit,
  runTimeout = runTimeout,
)
