package com.github.rocketraman.bootable.config

import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.ConfigBinder as UpstreamConfigBinder
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.secrets.Obfuscator
import com.sksamuel.hoplite.secrets.SecretsPolicy
import com.sksamuel.hoplite.secrets.StandardSecretsPolicy
import com.sksamuel.hoplite.secrets.StrictObfuscator
import com.sksamuel.hoplite.sources.SystemPropertiesPropertySource
import com.sksamuel.hoplite.sources.UserSettingsPropertySource
import com.sksamuel.hoplite.sources.XdgConfigPropertySource
import com.sksamuel.hoplite.transformer.PathNormalizer
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.new
import org.kodein.di.singleton

/**
 * A default Hoplite-based configuration module, that reads properties in the following order, from highest priority
 * to lowest:
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
 * If report is set to true (which it is by default), a report of all config keys and values will be enabled. All
 * sensitive values should be of type `Secret` to avoid leaking them in the report, named with "secret", "credential",
 * or "pass" in the name, have pre-processor metadata attached that identifies the value as a secret. To configure
 * this, pass a custom [SecretsPolicy].
 */
@Suppress("unused")
val configModule by lazy { configModule() }

fun configModule(
  withConfigStage1: ConfigLoaderBuilder.() -> ConfigLoaderBuilder = { this },
  withConfigStage2: ConfigLoaderBuilder.() -> ConfigLoaderBuilder = { this },
  withConfigStage3: ConfigLoaderBuilder.() -> ConfigLoaderBuilder = { this },
  decoders: Iterable<Decoder<*>> = emptyList(),
  secretsPolicy: SecretsPolicy = StandardSecretsPolicy,
  obfuscator: Obfuscator = StrictObfuscator(),
  report: Boolean = true,
  strict: Boolean = false,
) = DI.Module("configModule") {
  bind<UpstreamConfigBinder> {
    singleton {
      @OptIn(ExperimentalHoplite::class)
      ConfigLoaderBuilder.empty()
        .addDefaultDecoders()
        .addDecoders(decoders)
        .addDefaultPreprocessors()
        .addDefaultNodeTransformers()
        .addDefaultParamMappers()
        .withConfigStage1()
        .addIdiomaticEnvironmentSource()
        .addPropertySource(SystemPropertiesPropertySource) // requires config.override. prefix
        .addPropertySource(UserSettingsPropertySource)
        .addPropertySource(XdgConfigPropertySource)
        .withConfigStage2()
        .addDefaultParsers()
        .addResourceSource("/application-local.conf", optional = true, allowEmpty = true)
        .addResourceSource("/application.conf")
        .withConfigStage3()
        .apply {
          if (report) {
            withReport()
            withSecretsPolicy(secretsPolicy)
            withObfuscator(obfuscator)
          }
          if (strict) {
            strict()
          }
        }
        .withExplicitSealedTypes()
        .build()
        .configBinder()
    }
  }

  bind<ConfigBinder> {
    singleton { new(::HopliteConfigBinder) }
  }
}
