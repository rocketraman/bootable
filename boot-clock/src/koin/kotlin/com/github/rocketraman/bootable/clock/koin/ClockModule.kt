package com.github.rocketraman.bootable.clock.koin

import com.github.rocketraman.bootable.clock.asJavaClock
import java.time.Clock as JClock
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * A Koin module that defines the application's clock, [clock], `Clock.System` by default, as a `kotlin.time.Clock`,
 * and the same clock as a `java.time.Clock` in UTC, with [asJavaClock].
 *
 * The `java.time.Clock` definition gets the `kotlin.time.Clock` definition, so a test that defines its own
 * `kotlin.time.Clock` after this module, with `single<Clock> { testClock }`, or that includes `clockModule(testClock)`,
 * sets both. The Bootable modules that take a `kotlin.time.Clock` definition, if there is one, take this one.
 */
fun clockModule(clock: Clock = Clock.System): Module = module {
  single<Clock> { clock }
  single<JClock> { get<Clock>().asJavaClock() }
}
