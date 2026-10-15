package com.github.rocketraman.bootable.config

/**
 * Supertype for a module's config bindings, so each binding is just a type and a key. Extend it from a
 * contributed **interface** — Metro merges contributed interfaces as graph supertypes and fills [configBinder]
 * as a graph accessor, so your `@Provides` functions reach it without taking it as a parameter:
 *
 * ```
 * @ContributesTo(AppScope::class)
 * interface ServerModule : ConfigBindings {
 *   @Provides fun serverConfig(): ServerConfig = config("server")
 *   @Provides fun helloConfig(): HelloConfig = config("hello")
 * }
 * ```
 *
 * Requiring [configBinder] keeps the wiring compile-time checked — a graph with no `ConfigBinder` fails with
 * `[Metro/MissingBinding] No binding found for ConfigBinder`.
 */
interface ConfigBindings {
  val configBinder: ConfigBinder
}

/** Reified decode of a config section into [T]; see [ConfigBindings]. */
inline fun <reified T : Any> ConfigBindings.config(prefix: String): T = configBinder.configOf(prefix, T::class)
