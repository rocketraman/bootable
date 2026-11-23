package com.github.rocketraman.bootable.test

import com.github.rocketraman.bootable.config.ConfigBinder
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * A [ConfigBinder] that a test gives its config sections to, already decoded, by prefix, so a test that wires the
 * application, with any of the DI flavors, needs no config files or config library:
 *
 * ```
 * val binder = StaticConfigBinder(
 *   "health.ktor" to KtorHealthConfig(hostPort = "127.0.0.1:0"),
 *   "build" to BuildInfo(version = "1.4.2"),
 * )
 * ```
 *
 * Asked for a section it doesn't have, or for one of another type, it fails, naming the prefix.
 */
class StaticConfigBinder(vararg sections: Pair<String, Any>) : ConfigBinder {
  private val sections = sections.toMap()

  init {
    require(this.sections.size == sections.size) { "Each prefix can have only one section" }
  }

  override fun <T : Any> configOf(prefix: String, type: KClass<T>): T {
    val section = sections[prefix]
      ?: error("No config at $prefix, for ${type.simpleName}: there is config only at ${sections.keys}")
    check(type.isInstance(section)) {
      "The config at $prefix is a ${section::class.simpleName}, not a ${type.simpleName}"
    }
    return type.cast(section)
  }
}
