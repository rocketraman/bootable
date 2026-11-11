@file:JvmName("ConfigModuleInara")

package com.github.rocketraman.bootable.config

import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that decodes config with a [ConfigBinder], e.g. boot-config-hoplite's `HopliteConfigModule()`.
 * Build it once, and pass it to `BootableModule(…)`, which your application's module extends, and which the other
 * Bootable modules read their config from. Bind your own config types with [config]:
 *
 * ```
 * @DIModule(composed = true)
 * interface AppModule : BootableModule {
 *   val serverConfig: ServerConfig
 * }
 *
 * val bootable = BootableModule(HopliteConfigModule())
 * AppModule(
 *   bootableModule = bootable,
 *   serverConfig = bootable.config("server"),
 *   …
 * )
 * ```
 */
@DIModule
interface ConfigModule {
  val configBinder: ConfigBinder
}

/** A binding, for any module, that decodes the [prefix] config section into a [T], once. */
inline fun <reified T : Any> ConfigModule.config(prefix: String): Binding<Any?, Unit, T> =
  Binding.detachedSingle { configBinder.config<T>(prefix) }

/** A [ConfigModule] with no config, for an application that reads none: its [EmptyConfigBinder] fails every read. */
val EmptyConfigModule: ConfigModule = ConfigModule(configBinder = Binding.value(EmptyConfigBinder))
