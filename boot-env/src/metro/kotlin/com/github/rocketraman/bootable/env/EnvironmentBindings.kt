package com.github.rocketraman.bootable.env

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.LifecycleController
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Provides [EnvironmentConfig], decoded from the `env` config section. */
@ContributesTo(BootScope::class)
@BindingContainer
object EnvironmentConfigBindings {
  @Provides @SingleIn(BootScope::class)
  fun environmentConfig(binder: ConfigBinder): EnvironmentConfig = binder.config("env")
}

/** Registers the [EnvironmentInitializer] that populates the global [environment] before services start. */
@ContributesTo(BootScope::class)
@BindingContainer
object EnvironmentBindings {
  @Provides @IntoSet
  fun environmentInitializer(config: EnvironmentConfig): LifecycleController = EnvironmentInitializer(config)
}
