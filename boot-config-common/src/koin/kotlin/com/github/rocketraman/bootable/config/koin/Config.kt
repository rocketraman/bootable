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
 * The application needs a `ConfigBinder` definition, e.g. from boot-config-hoplite's `hopliteConfigModule()`. To
 * define the section as a supertype of the type it decodes as, use [configAs].
 */
inline fun <reified T : Any> Module.config(prefix: String, qualifier: Qualifier? = null): KoinDefinition<T> =
  single<T>(qualifier) { get<ConfigBinder>().configOf(prefix, T::class) }

/**
 * Defines a single [T], decoded once as a [C] from the [prefix] config section by the application's [ConfigBinder],
 * with an optional [qualifier]. For a section whose type is an implementation of the type it is defined as:
 *
 * ```
 * fun sqliteConfigModule() = module {
 *   configAs<JdbcConfig, SqliteConfig>("db.jdbc")
 * }
 * ```
 */
inline fun <reified T : Any, reified C : T> Module.configAs(prefix: String, qualifier: Qualifier? = null): KoinDefinition<T> =
  single<T>(qualifier) { get<ConfigBinder>().configOf(prefix, C::class) }
