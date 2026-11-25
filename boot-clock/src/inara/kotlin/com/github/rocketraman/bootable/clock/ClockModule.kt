@file:JvmName("ClockModuleInara")

package com.github.rocketraman.bootable.clock

import java.time.Clock as JClock
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import kotlin.time.Clock

/**
 * The Inara module that provides the application's clock, as a `kotlin.time.Clock` and as a `java.time.Clock`, for
 * the application's own modules to require. Compose [clockModule], with `Clock.System`, or, in a test, with a clock
 * of its own.
 */
@DIModule
interface ClockModule {
  val clock: Clock
  val javaClock: JClock
}

/** A [ClockModule] on [clock], whose `java.time.Clock` is [clock] in UTC, with [asJavaClock]. */
fun clockModule(clock: Clock = Clock.System): ClockModule = ClockModule(
  clock = Binding.value(clock),
  javaClock = Binding.single { this.clock.asJavaClock() },
)
