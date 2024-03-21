package com.github.rocketraman.bootable.config

import com.jdiazcano.cfg4k.hocon.HoconConfigLoader
import com.jdiazcano.cfg4k.loaders.EnvironmentConfigLoader
import com.jdiazcano.cfg4k.loaders.SystemPropertyConfigLoader
import com.jdiazcano.cfg4k.providers.ConfigProvider
import com.jdiazcano.cfg4k.providers.OverrideConfigProvider
import com.jdiazcano.cfg4k.providers.ProxyConfigProvider
import com.jdiazcano.cfg4k.providers.cache
import com.jdiazcano.cfg4k.reloadstrategies.FileChangeReloadStrategy
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.kodein.di.*
import java.io.File

/**
 * A default configuration module, that reads properties in the following order, from highest priority to lowest:
 * - environment variables
 * - system properties
 * - `application-local.conf` in resources
 * - `application.conf` in resources
 *
 * For more control, use the `configModule()` function which has stage 1, 2, and 3 hooks to Typesafe [Config] with
 * the following priorities:
 * - stage1
 * - environment variables
 * - system properties
 * - stage2
 * - `application-local.conf` in resources
 * - `application.conf` in resources
 * - stage3
 */
@Suppress("unused")
val configModule by lazy { configModule() }

fun configModule(
  withConfigStage1: Config.() -> Config = { this },
  withConfigStage2: Config.() -> Config = { this },
  withConfigStage3: Config.() -> Config = { this },
) = DI.Module("configModule") {
  bind<ConfigProvider> {
    singleton {
      OverrideConfigProvider(
        ProxyConfigProvider(EnvironmentConfigLoader()),
        ProxyConfigProvider(SystemPropertyConfigLoader()),
        ProxyConfigProvider(HoconConfigLoader {
          // this is a workaround for https://github.com/jdiazcano/cfg4k/issues/54
          // we use typesafe config directly to perform the props overrides within HOCON
          ConfigFactory
            .empty()
            .withConfigStage1()
            .withFallback(ConfigFactory.systemEnvironment())
            .withFallback(ConfigFactory.systemProperties())
            .withConfigStage2()
            .withFallback(ConfigFactory.parseResources("application-local.conf"))
            .withFallback(ConfigFactory.parseResources("application.conf"))
            .withConfigStage3()
        })
      ).cache()
    }
  }

  bind<ConfigProvider>("reloadable") with multiton { file: File ->
    ProxyConfigProvider(HoconConfigLoader(file), FileChangeReloadStrategy(file)).cache()
  }

  bind<ConfigBinder> {
    singleton { new(::Cfg4kConfigBinder) }
  }
}
