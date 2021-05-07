package com.github.rocketraman.bootable.config

import com.jdiazcano.cfg4k.providers.ConfigProvider
import com.jdiazcano.cfg4k.providers.bind

/**
 * Wraps a configuration interface T, and provides an implementation of `ConfigListener` for when that
 * configuration is updated which delegates to a function set via the [reloadListener] property. Generally
 * one create a class that receives an instance of this class, obtains the config object from it, and then
 * registers a function to be called when the config provider has reloaded by setting [reloadListener].
 */
@Suppress("unused")
class ListenableConfig<out T: Any>(val config: T, provider: ConfigProvider? = null) {
  companion object {
    /**
     * When creating an instance using this method, automatically uses the provider to bind the config with
     * the given name, and then registers itself as a listener for config reloads from the supplied [ConfigProvider].
     */
    inline operator fun <reified T: Any> invoke(provider: ConfigProvider, bindName: String): ListenableConfig<T> =
      ListenableConfig(provider.bind(bindName), provider)
  }

  init {
    provider?.addReloadListener(::triggerConfigReloaded)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  var reloadListener: (() -> Unit)? = null

  private fun triggerConfigReloaded() {
    reloadListener?.invoke()
  }
}
