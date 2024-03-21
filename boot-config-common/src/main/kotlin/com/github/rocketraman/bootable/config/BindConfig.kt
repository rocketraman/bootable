package com.github.rocketraman.bootable.config

import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import kotlin.reflect.KClass

interface ConfigBinder {
  fun <T: Any> configOf(prefix: String, type: KClass<T>): T
}

/**
 * Convenience function for binding configuration classes in Kodein modules via hoplite. To use it,
 * simply do `bindConfig<ConfigClassToBind>()`.
 *
 * At the moment, we do not support prefix-based bindings, see https://github.com/sksamuel/hoplite/issues/386.
 */
inline fun <reified T: Any> DI.Builder.bindConfig(key: String, tag: Any? = null) {
  bind(tag) { singleton { instance<ConfigBinder>().configOf(key, T::class) } }
}
