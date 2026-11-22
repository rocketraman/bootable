package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.boot.AppService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

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

/**
 * Starts this service, as Bootable does, in a scope of its own, whose failures go to [onFailure], as Bootable's stop the
 * application.
 */
internal suspend fun AppService.startService(
  onFailure: (Throwable) -> Unit = { throw AssertionError("The service failed", it) },
) = start(CoroutineScope(Job() + CoroutineExceptionHandler { _, e -> onFailure(e) }))

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
