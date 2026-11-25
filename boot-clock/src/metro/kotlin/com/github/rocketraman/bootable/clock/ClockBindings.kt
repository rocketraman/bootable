package com.github.rocketraman.bootable.clock

import com.github.rocketraman.bootable.boot.BootScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import java.time.Clock as JClock
import kotlin.time.Clock

/**
 * Provides the application's clock, `Clock.System`, as a `kotlin.time.Clock`, and the same clock as a
 * `java.time.Clock` in UTC, with [asJavaClock].
 *
 * The `java.time.Clock` is the graph's `kotlin.time.Clock`, so a test that gives its graph another `kotlin.time.Clock`,
 * such as with a binding container of its own passed to `createDynamicGraph`, sets both. The Bootable bindings that
 * take the graph's `kotlin.time.Clock`, if it has one, take this one.
 */
@ContributesTo(BootScope::class)
interface ClockBindings {
  companion object {
    @Provides fun clock(): Clock = Clock.System

    @Provides fun javaClock(clock: Clock): JClock = clock.asJavaClock()
  }
}
