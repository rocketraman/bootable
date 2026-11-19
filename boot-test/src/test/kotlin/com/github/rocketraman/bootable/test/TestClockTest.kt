package com.github.rocketraman.bootable.test

import de.infix.testBalloon.framework.core.testSuite
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

val TestClockTest by testSuite {
  test("stands still until moved") {
    val clock = TestClock()
    assert(clock.now() == TestClock.DEFAULT_START)
    assert(clock.now() == TestClock.DEFAULT_START)

    assert(clock.advanceBy(5.minutes) == TestClock.DEFAULT_START + 5.minutes)
    assert(clock.now() == TestClock.DEFAULT_START + 5.minutes)

    val later = Instant.parse("2030-06-01T12:00:00Z")
    clock.set(later)
    assert(clock.now() == later)
  }
}
