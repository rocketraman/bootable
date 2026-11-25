package com.github.rocketraman.bootable.clock

import java.time.Clock as JClock
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import kotlin.time.Clock

/**
 * A Kodein module that binds the application's clock, [clock], `Clock.System` by default, as a `kotlin.time.Clock`,
 * and the same clock as a `java.time.Clock` in UTC, with [asJavaClock].
 *
 * The `java.time.Clock` binding wraps the `kotlin.time.Clock` binding, so a test that binds its own `kotlin.time.Clock`,
 * with `bindSingleton<Clock>(overrides = true) { testClock }`, or that imports `clockModule(testClock)`, sets both.
 * The Bootable modules that take a `kotlin.time.Clock` binding, if there is one, take this one.
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
fun clockModule(clock: Clock = Clock.System): DI.Module = DI.Module("clockModule") {
  bindSingleton<Clock> { clock }
  bindSingleton<JClock> { instance<Clock>().asJavaClock() }
}
