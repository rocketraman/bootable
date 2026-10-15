package com.github.rocketraman.bootable.env

import com.github.rocketraman.bootable.boot.LifecycleController
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that decodes [EnvironmentConfig] from the `env` section and registers the
 * [EnvironmentInitializer] (which populates the global [environment]) into the lifecycle-controller set.
 * Requires a `ConfigBinder` binding (e.g. from boot-config-hoplite's `configModule`).
 */
val environmentModule by DI.Module {
  bindSingleton<EnvironmentConfig> { instance<ConfigBinder>().config("env") }
  inBindSet<LifecycleController> { add { singleton { EnvironmentInitializer(instance()) } } }
}
