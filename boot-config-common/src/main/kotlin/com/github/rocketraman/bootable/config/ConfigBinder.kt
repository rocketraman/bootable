package com.github.rocketraman.bootable.config

import kotlin.reflect.KClass

interface ConfigBinder {
  fun <T: Any> configOf(prefix: String, type: KClass<T>): T
}

/**
 * Reified convenience for decoding a config section into [T] via a [ConfigBinder]:
 *
 * ```
 * val binder = hopliteConfigBinder()
 * val serverConfig = binder.config<ServerConfig>("server")
 * ```
 */
inline fun <reified T: Any> ConfigBinder.config(prefix: String): T = configOf(prefix, T::class)

/** A [ConfigBinder] with no config, for an application that reads none. Every read fails. */
object EmptyConfigBinder : ConfigBinder {
  override fun <T : Any> configOf(prefix: String, type: KClass<T>): T =
    throw IllegalStateException("No config to read the $prefix section from: this application was given no ConfigBinder")
}
