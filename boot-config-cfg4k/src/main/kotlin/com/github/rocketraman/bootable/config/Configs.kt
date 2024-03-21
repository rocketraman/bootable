package com.github.rocketraman.bootable.config

import com.jdiazcano.cfg4k.providers.ConfigProvider
import org.kodein.di.*
import org.kodein.di.bindings.NoArgBindingDI
import java.io.File

/**
 * Convenience function for binding a listenable config from a specific file. If [ignoreMissing] is true, then
 * a regular non-reloadable ConfigProvider is used.
 *
 * TODO move this interface to boot-common, once we have hoplite figured out.
 */
inline fun <reified T: Any> DI.Builder.bindListenableConfigFile(path: String, key: String, tag: Any? = null, ignoreMissing: Boolean = false) {
  bind(tag) { singleton { ListenableConfig<T>(reloadableConfigProviderByFile(path, ignoreMissing), key) } }
}

@PublishedApi internal fun <T: Any> NoArgBindingDI<T>.reloadableConfigProviderByFile(path: String, ignoreMissing: Boolean): ConfigProvider {
  val file = File(path).apply {
    if(!exists()) {
      if(ignoreMissing) return instance()
      else error("Required configuration file $path does not exist.")
    }
  }
  return factory<File, ConfigProvider>("reloadable")(file)
}
