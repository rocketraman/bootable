package com.github.rocketraman.bootable.config

import com.sksamuel.hoplite.ConfigLoader
import kotlin.reflect.KClass

class HopliteConfigBinder(private val configLoader: ConfigLoader): ConfigBinder {
  override fun <T: Any> configOf(prefix: String, type: KClass<T>): T = configLoader.loadConfigOrThrow(type, emptyList())
}
