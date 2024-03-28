package com.github.rocketraman.bootable.config

import com.sksamuel.hoplite.ConfigBinder as UpstreamConfigBinder
import kotlin.reflect.KClass

class HopliteConfigBinder(private val binder: UpstreamConfigBinder): ConfigBinder {
  override fun <T: Any> configOf(prefix: String, type: KClass<T>): T =
    binder.bindOrThrow(type, prefix)
}
