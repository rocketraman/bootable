@file:JvmName("EnvironmentModuleInara")

package com.github.rocketraman.bootable.env

import com.github.rocketraman.bootable.boot.LifecycleControllers
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.config
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that populates the global [environment], reading its config from the [ConfigModule] it requires.
 * Build it with `environmentModule()`, and compose it into the application's module. Its [environmentInitializer] is
 * one of the application's lifecycle controllers, so it runs before the services start.
 */
@DIModule
interface EnvironmentModule : @DIModule.Required ConfigModule {
  val environmentConfig: EnvironmentConfig
  @LifecycleControllers val environmentInitializer: EnvironmentInitializer
}

/** An [EnvironmentModule], with its [EnvironmentConfig] decoded from the `env` config section. */
fun environmentModule(): EnvironmentModulePart = EnvironmentModulePart(
  environmentConfig = config("env"),
  environmentInitializer = Binding.single { EnvironmentInitializer(environmentConfig) },
)
