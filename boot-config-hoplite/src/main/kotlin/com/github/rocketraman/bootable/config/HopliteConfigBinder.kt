package com.github.rocketraman.bootable.config

import com.sksamuel.hoplite.ConfigLoader
import kotlin.reflect.KClass

class HopliteConfigBinder(private val configLoaderFn: (String) -> ConfigLoader): ConfigBinder {
  override fun <T: Any> configOf(prefix: String, type: KClass<T>): T =
    configLoaderFn(prefix).loadConfigOrThrow(type, emptyList())
}
