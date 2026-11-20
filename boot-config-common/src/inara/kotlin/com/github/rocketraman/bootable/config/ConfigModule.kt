@file:JvmName("ConfigModuleInara")

package com.github.rocketraman.bootable.config

import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that decodes config with a [ConfigBinder], e.g. boot-config-hoplite's `HopliteConfigModule()`.
 * Your application's module extends it, and the Bootable modules require it, to read their config from it. Bind your
 * own config types with [config]:
 *
 * ```
 * @DIModule(composed = true)
 * interface AppModule : ConfigModule, BootableModule {
 *   val serverConfig: ServerConfig
 * }
 *
 * AppModule(
 *   configModule = HopliteConfigModule(),
 *   bootableModule = bootableModule(),
 *   serverConfig = config("server"),
 * )
 * ```
 */
@DIModule
interface ConfigModule {
  val configBinder: ConfigBinder
}

/** A binding, for any [ConfigModule], that decodes the [prefix] config section into a [T], once. */
inline fun <reified T : Any> config(prefix: String): Binding<ConfigModule, Unit, T> =
  Binding.single { configBinder.config<T>(prefix) }

/** A [ConfigModule] with no config, for an application that reads none: its [EmptyConfigBinder] fails every read. */
val EmptyConfigModule: ConfigModule = ConfigModule(configBinder = Binding.value(EmptyConfigBinder))
