package com.github.rocketraman.bootable.clock

import com.github.rocketraman.bootable.test.TestClock
import de.infix.testBalloon.framework.core.testSuite
import java.time.Clock as JClock
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

val ClocksTest by testSuite {
  test("a kotlin clock as a java clock wraps the kotlin clock, in UTC") {
    val clock = TestClock()
    val javaClock = clock.asJavaClock()
    assert(javaClock.zone == ZoneOffset.UTC)
    assert(javaClock.instant() == TestClock.DEFAULT_START.toJavaInstant())
    clock.advanceBy(5.minutes)
    assert(javaClock.instant() == (TestClock.DEFAULT_START + 5.minutes).toJavaInstant())
    assert(javaClock.millis() == (TestClock.DEFAULT_START + 5.minutes).toEpochMilliseconds())
  }

  test("a kotlin clock as a java clock in a zone, and in another") {
    val clock = TestClock()
    val toronto = ZoneId.of("America/Toronto")
    val javaClock = clock.asJavaClock(toronto)
    assert(javaClock.zone == toronto)
    assert(javaClock.withZone(toronto) == javaClock)
    val inUtc = javaClock.withZone(ZoneOffset.UTC)
    assert(inUtc.zone == ZoneOffset.UTC)
    assert(inUtc == clock.asJavaClock())
    assert(inUtc.instant() == javaClock.instant())
  }

  test("the system clock as a java clock is java's system clock") {
    assert(Clock.System.asJavaClock() == JClock.systemUTC())
    val toronto = ZoneId.of("America/Toronto")
    assert(Clock.System.asJavaClock(toronto) == JClock.system(toronto))
  }

  test("a java clock as a kotlin clock wraps the java clock") {
    val javaClock = JClock.fixed(TestClock.DEFAULT_START.toJavaInstant(), ZoneOffset.UTC)
    val clock = javaClock.asKotlinClock()
    assert(clock.now() == TestClock.DEFAULT_START)
    assert(javaClock.instant().toKotlinInstant() == clock.now())
  }

  test("java's system clock, in any zone, as a kotlin clock is the system clock") {
    assert(JClock.systemUTC().asKotlinClock() == Clock.System)
    assert(JClock.system(ZoneId.of("America/Toronto")).asKotlinClock() == Clock.System)
  }

  test("a clock converted and converted back is the clock it was") {
    val clock = TestClock()
    assert(clock.asJavaClock().asKotlinClock() === clock)
    val javaClock = JClock.fixed(TestClock.DEFAULT_START.toJavaInstant(), ZoneOffset.UTC)
    assert(javaClock.asKotlinClock().asJavaClock() == javaClock)
  }
}
