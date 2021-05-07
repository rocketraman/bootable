package com.github.rocketraman.bootable.config

import com.jdiazcano.cfg4k.hocon.HoconConfigLoader
import com.jdiazcano.cfg4k.loaders.EnvironmentConfigLoader
import com.jdiazcano.cfg4k.loaders.SystemPropertyConfigLoader
import com.jdiazcano.cfg4k.providers.ConfigProvider
import com.jdiazcano.cfg4k.providers.OverrideConfigProvider
import com.jdiazcano.cfg4k.providers.ProxyConfigProvider
import com.jdiazcano.cfg4k.providers.cache
import com.jdiazcano.cfg4k.reloadstrategies.FileChangeReloadStrategy
import com.typesafe.config.ConfigFactory
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.multiton
import org.kodein.di.singleton
import java.io.File

@Suppress("unused")
val configModule = DI.Module("configModule") {
  bind<ConfigProvider>() with singleton { OverrideConfigProvider(
    ProxyConfigProvider(EnvironmentConfigLoader()),
    ProxyConfigProvider(SystemPropertyConfigLoader()),
    ProxyConfigProvider(HoconConfigLoader {
      // this is a workaround for https://github.com/jdiazcano/cfg4k/issues/54
      // we use typesafe config directly to perform the props overrides within HOCON
      ConfigFactory
        // note HOCON does no environment name mangling, so properties are brought in exactly as they are in the
        // environment, and don't appear to override hierarchical values without explicit reference -- best to use
        // -D properties for override
        .systemEnvironment()
        .withFallback(ConfigFactory.systemProperties())
        .withFallback(ConfigFactory.parseResources("application-local.conf"))
        .withFallback(ConfigFactory.parseResources("application.conf"))
    })
  ).cache() }

  bind<ConfigProvider>("reloadable") with multiton { file: File ->
    ProxyConfigProvider(HoconConfigLoader(file), FileChangeReloadStrategy(file)).cache()
  }
}
