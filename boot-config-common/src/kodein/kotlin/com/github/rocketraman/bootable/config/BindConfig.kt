package com.github.rocketraman.bootable.config

import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * Binds a [T], decoded once from the [prefix] config section by the DI's [ConfigBinder], with an optional [tag]:
 *
 * ```
 * val appModule by DI.Module {
 *   bindConfig<ServerConfig>("server")
 * }
 * ```
 *
 * The DI needs a `ConfigBinder` binding, e.g. from boot-config-hoplite's `hopliteConfigModule`. To bind the section
 * as a supertype of the type it decodes as, use [bindConfigAs].
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
inline fun <reified T : Any> DI.Builder.bindConfig(prefix: String, tag: Any? = null) {
  bind<T>(tag) { singleton { instance<ConfigBinder>().config<T>(prefix) } }
}

/**
 * Binds a [T], decoded once as a [C] from the [prefix] config section by the DI's [ConfigBinder], with an optional
 * [tag]. For a section whose type is an implementation of the type it is bound as:
 *
 * ```
 * val sqliteConfigModule by DI.Module {
 *   bindConfigAs<JdbcConfig, SqliteConfig>("db.jdbc")
 * }
 * ```
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
inline fun <reified T : Any, reified C : T> DI.Builder.bindConfigAs(prefix: String, tag: Any? = null) {
  bind<T>(tag) { singleton { instance<ConfigBinder>().config<C>(prefix) } }
}
