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
