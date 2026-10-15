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
