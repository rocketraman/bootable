package com.github.rocketraman.bootable.config

/**
 * Provides a [ConfigBinder] to a module's config bindings, so each binding is just a type and a key. Extend it from a
 * contributed **interface** — Metro merges contributed interfaces as graph supertypes and fills [configBinder]
 * as a graph accessor, so your `@Provides` functions reach it without taking it as a parameter:
 *
 * ```
 * @ContributesTo(AppScope::class)
 * interface ServerConfigBindings : ConfigBinderProvider {
 *   @Provides fun serverConfig(): ServerConfig = config("server")
 *   @Provides fun helloConfig(): HelloConfig = config("hello")
 * }
 * ```
 *
 * Requiring [configBinder] keeps the wiring compile-time checked — a graph with no `ConfigBinder` fails with
 * `[Metro/MissingBinding] No binding found for ConfigBinder`.
 */
interface ConfigBinderProvider {
  val configBinder: ConfigBinder
}

/** Reified decode of a config section into [T]; see [ConfigBinderProvider]. */
inline fun <reified T : Any> ConfigBinderProvider.config(prefix: String): T = configBinder.configOf(prefix, T::class)
