@file:JvmName("ConfigKoin")

package com.github.rocketraman.bootable.config.koin

import com.github.rocketraman.bootable.config.ConfigBinder
import org.koin.core.definition.KoinDefinition
import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier

/**
 * Defines a single [T], decoded once from the [prefix] config section by the application's [ConfigBinder], with an
 * optional [qualifier]:
 *
 * ```
 * val appModule = module {
 *   config<ServerConfig>("server")
 * }
 * ```
 *
 * The application needs a `ConfigBinder` definition, e.g. from boot-config-hoplite's `hopliteConfigModule()`.
 */
inline fun <reified T : Any> Module.config(prefix: String, qualifier: Qualifier? = null): KoinDefinition<T> =
  single<T>(qualifier) { get<ConfigBinder>().configOf(prefix, T::class) }
