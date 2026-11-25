package com.github.rocketraman.bootable.clock

import java.time.Clock as JClock
import java.time.Instant as JInstant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * Wraps the Kotlin `Clock` receiver as a `java.time.Clock` in [zone], UTC by default.
 *
 * The `java.time.Clock` wrapper simply delegates to the underlying Kotlin `Clock`.
 * A good use case for this is to adapt a Kotlin `Clock`, such as a test clock, to a library that takes a
 * `java.time.Clock`, without creating a new clock.
 *
 * [Clock.System] becomes `java.time.Clock.system(zone)`, such as `java.time.Clock.systemUTC()`. A receiver from
 * [asKotlinClock] is unwrapped: the result is simply the original wrapped `java.time.Clock`, in [zone].
 */
fun Clock.asJavaClock(zone: ZoneId = ZoneOffset.UTC): JClock = when (this) {
  Clock.System -> JClock.system(zone)
  is KotlinClockOfJava -> javaClock.withZone(zone)
  else -> JavaClockOfKotlin(this, zone)
}

/**
 * Wraps the `java.time.Clock` receiver as a Kotlin `Clock`.
 *
 * The Kotlin `Clock` wrapper simply delegates to the underlying `java.time.Clock`.
 * A good use case for this is to adapt a `java.time.Clock` to a Kotlin `Clock`, without creating a new clock, such as
 * in tests.
 *
 * A receiver from [asJavaClock] is unwrapped: the result is simply the original wrapped Kotlin `Clock`.
 * `java.time.Clock.systemUTC()`, or any other system clock, becomes [Clock.System].
 */
fun JClock.asKotlinClock(): Clock = when {
  this is JavaClockOfKotlin -> clock
  this == JClock.system(zone) -> Clock.System
  else -> KotlinClockOfJava(this)
}

private class JavaClockOfKotlin(val clock: Clock, private val zone: ZoneId) : JClock() {
  override fun getZone(): ZoneId = zone
  override fun withZone(zone: ZoneId): JClock = if (zone == this.zone) this else JavaClockOfKotlin(clock, zone)
  override fun instant(): JInstant = clock.now().toJavaInstant()
  override fun equals(other: Any?) = other is JavaClockOfKotlin && other.clock == clock && other.zone == zone
  override fun hashCode() = clock.hashCode() xor zone.hashCode()
  override fun toString() = "JavaClock($clock, $zone)"
}

private class KotlinClockOfJava(val javaClock: JClock) : Clock {
  override fun now(): Instant = javaClock.instant().toKotlinInstant()
  override fun equals(other: Any?) = other is KotlinClockOfJava && other.javaClock == javaClock
  override fun hashCode() = javaClock.hashCode()
  override fun toString() = "KotlinClock($javaClock)"
}
