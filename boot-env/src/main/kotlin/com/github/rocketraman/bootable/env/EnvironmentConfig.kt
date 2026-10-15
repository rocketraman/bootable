package com.github.rocketraman.bootable.env

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config

interface EnvironmentConfig {
  val environment: String
}

/**
 * Decodes [EnvironmentConfig] from the `env` config section using the given [ConfigBinder].
 */
fun environmentConfig(binder: ConfigBinder): EnvironmentConfig = binder.config("env")

sealed class Env {
  data object Dev : Env()
  data object Test : Env()
  data object Prod : Env()
  data class Custom(val name: String) : Env()

  companion object {
    fun fromString(s: String) = when (s.lowercase()) {
      "dev" -> Dev
      "test" -> Test
      "prod" -> Prod
      else -> Custom(s)
    }
  }
}
