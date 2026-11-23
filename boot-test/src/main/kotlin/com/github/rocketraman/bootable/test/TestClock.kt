package com.github.rocketraman.bootable.test

import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A [Clock] a test sets: it stands still at [now] until the test moves it with [advanceBy] or [set]. Safe to read and
 * move from any thread.
 *
 * Bind it where the application takes a `kotlin.time.Clock`, such as `OpenTelemetryService` and
 * `ServiceStartTracing`, which take their time from the `Clock` binding, with Koin, Kodein, or Metro, if there is one. For a
 * monotonic `TimeSource`, use the standard library's `kotlin.time.TestTimeSource`.
 */
class TestClock(start: Instant = DEFAULT_START) : Clock {
  private val current = AtomicReference(start)

  override fun now(): Instant = current.get()

  /** Moves the clock forward by [duration], or back, if it is negative, and returns the new time. */
  fun advanceBy(duration: Duration): Instant = current.updateAndGet { it + duration }

  /** Sets the clock to [instant]. */
  fun set(instant: Instant) {
    current.set(instant)
  }

  override fun toString() = "TestClock(${now()})"

  companion object {
    /** Where a [TestClock] starts by default: a fixed time, so a test's times are the same on every run. */
    val DEFAULT_START: Instant = Instant.parse("2026-01-01T00:00:00Z")
  }
}
