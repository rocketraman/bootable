package com.github.rocketraman.bootable.config

import com.github.rocketraman.bootable.boot.BootScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Contributes the default Hoplite [ConfigBinder] into a Bootable graph. */
@ContributesTo(BootScope::class)
@BindingContainer
object HopliteConfigBindings {
  @Provides @SingleIn(BootScope::class) fun configBinder(): ConfigBinder = hopliteConfigBinder()
}
