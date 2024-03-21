package com.github.rocketraman.bootable.config

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource
import com.sksamuel.hoplite.sources.*
import org.kodein.di.*

/**
 * NOTE: At the moment hoplite does not support a prefix-based configuration
 * (https://github.com/sksamuel/hoplite/issues/386) which severely limits its ability to be used in a modular
 * way. Until that issue is resolved (which according to the author should be trivial), we recommend using
 * cfg4k instead.
 *
 * A default configuration module, that reads properties in the following order, from highest priority to lowest:
 * - environment variables (supports upper case, convert `__` to `.`)
 * - system properties (with `config.override.` prefix)
 * - `application-local.conf` in resources
 * - `application.conf` in resources
 *
 * For more control, use the `configModule()` function which has stage 1, 2, and 3 hooks to Hoplite with
 * the following priorities:
 * - stage1
 * - environment variables
 * - system properties
 * - user settings
 * - user settings via XDG config
 * - stage2
 * - `application-local.conf` in resources
 * - `application.conf` in resources
 * - stage3
 *
 * If report is set to true, a report of all config keys and values will be enabled. All sensitive values should
 * be wrapped in `Masked` or `Secret` to avoid leaking them in the report. Report is disabled by default to avoid
 * printing sensitive values to logs unintentionally.
 */
@Suppress("unused")
val configModule by lazy { configModule() }

fun configModule(
  withConfigStage1: ConfigLoaderBuilder.() -> ConfigLoaderBuilder = { this },
  withConfigStage2: ConfigLoaderBuilder.() -> ConfigLoaderBuilder = { this },
  withConfigStage3: ConfigLoaderBuilder.() -> ConfigLoaderBuilder = { this },
  report: Boolean = true,
) = DI.Module("configModule") {
  bind<ConfigLoader> {
    singleton {
      ConfigLoaderBuilder.empty()
        .addDefaultDecoders()
        .addDefaultPreprocessors()
        .addDefaultParamMappers()
        .withConfigStage1()
        .addEnvironmentSource()
        .addPropertySource(SystemPropertiesPropertySource) // requires config.override. prefix
        .addPropertySource(UserSettingsPropertySource)
        .addPropertySource(XdgConfigPropertySource)
        .withConfigStage2()
        .addDefaultParsers()
        .addResourceSource("application-local.conf")
        .addResourceSource("application.conf")
        .withConfigStage3()
        .apply {
          if (report) withReport()
        }
        .build()
    }
  }

  bind<ConfigBinder> {
    singleton { new(::HopliteConfigBinder) }
  }
}
