package com.github.rocketraman.bootable.env

import com.github.rocketraman.bootable.boot.LifecycleController
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config

/**
 * Decodes [EnvironmentConfig] from the `env` config section using the given [ConfigBinder].
 */
fun environmentConfig(binder: ConfigBinder): EnvironmentConfig = binder.config("env")

/**
 * A [LifecycleController] that initializes the global [environment] from configuration before services start.
 * Add it to the set passed to `boot`:
 *
 * ```
 * boot(
 *   appServices = ...,
 *   lifecycleControllers = defaultLifecycleControllers() + environmentInitializer(binder),
 * )
 * ```
 */
fun environmentInitializer(binder: ConfigBinder): LifecycleController =
  EnvironmentInitializer(environmentConfig(binder))
