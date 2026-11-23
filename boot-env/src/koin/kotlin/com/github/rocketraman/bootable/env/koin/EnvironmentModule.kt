package com.github.rocketraman.bootable.env.koin

import com.github.rocketraman.bootable.boot.LifecycleController
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.env.EnvironmentConfig
import com.github.rocketraman.bootable.env.EnvironmentInitializer
import com.github.rocketraman.bootable.env.environment
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that decodes [EnvironmentConfig] from the `env` section and adds the [EnvironmentInitializer] (which
 * populates the global [environment]) to the lifecycle controllers. Requires a `ConfigBinder` definition (e.g. from
 * boot-config-hoplite's `hopliteConfigModule()`).
 */
fun environmentModule(): Module = module {
  single<EnvironmentConfig> { get<ConfigBinder>().config("env") }
  single<LifecycleController>(named<EnvironmentInitializer>()) { EnvironmentInitializer(get()) }
}
