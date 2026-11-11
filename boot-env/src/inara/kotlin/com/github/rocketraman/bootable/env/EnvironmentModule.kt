@file:JvmName("EnvironmentModuleInara")

package com.github.rocketraman.bootable.env

import com.github.rocketraman.bootable.boot.LifecycleControllerAcceptor
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.config
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that populates the global [environment]. Build it with the `EnvironmentModule(context)` builder,
 * which adds its [environmentInitializer] to the application's lifecycle controllers, so it runs before the services
 * start.
 */
@DIModule
interface EnvironmentModule {
  val environmentConfig: EnvironmentConfig
  val environmentInitializer: EnvironmentInitializer
}

/**
 * An [EnvironmentModule] with its [EnvironmentConfig] decoded from the `env` section of [context]'s config, and adding
 * its initializer to [context]'s lifecycle controllers, such as the `BootableModule`'s.
 */
fun <C> EnvironmentModule(
  context: C,
): EnvironmentModule where C : ConfigModule, C : LifecycleControllerAcceptor {
  val module = EnvironmentModule(
    environmentConfig = context.config("env"),
    environmentInitializer = Binding.single { EnvironmentInitializer(environmentConfig) },
  )
  context.addLifecycleController { module.environmentInitializer }
  return module
}
