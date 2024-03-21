package com.github.rocketraman.bootable.config

import com.jdiazcano.cfg4k.providers.ConfigProvider
import kotlin.reflect.KClass

class Cfg4kConfigBinder(private val configProvider: ConfigProvider): ConfigBinder {
  override fun <T: Any> configOf(prefix: String, type: KClass<T>): T = configProvider.bind(prefix, type.java)
}
