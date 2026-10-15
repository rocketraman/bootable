package com.github.rocketraman.bootable.config

import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.ConfigBinder as UpstreamConfigBinder
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.secrets.Obfuscator
import com.sksamuel.hoplite.secrets.SecretsPolicy
import com.sksamuel.hoplite.secrets.StandardSecretsPolicy
import com.sksamuel.hoplite.secrets.StrictObfuscator
import com.sksamuel.hoplite.sources.EnvironmentVariablesPropertySource
import com.sksamuel.hoplite.sources.SystemPropertiesPropertySource
import com.sksamuel.hoplite.sources.UserSettingsPropertySource
import com.sksamuel.hoplite.sources.XdgConfigPropertySource
import kotlin.reflect.KClass

class HopliteConfigBinder(private val binder: UpstreamConfigBinder): ConfigBinder {
  override fun <T: Any> configOf(prefix: String, type: KClass<T>): T =
    binder.bindOrThrow(type, prefix)
}

/**
 * Whether the environment variable [name] is set by the shell or other tooling and is never application config.
 * These are excluded from the environment source because their values are shell code that routinely contains
 * `${...}`, which Hoplite would otherwise fail on as an unresolved substitution.
 *
 * TODO remove once Hoplite excludes these itself: https://github.com/sksamuel/hoplite/issues/602
 */
fun isKnownNonConfigEnvVar(name: String): Boolean =
  // exported bash functions (`export -f`, or tools like Lmod), named `BASH_FUNC_name%%`
  name.startsWith("BASH_FUNC_") ||
    name in shellPromptEnvVars

private val shellPromptEnvVars = setOf("PS1", "PS2", "PS4", "PROMPT_COMMAND")

/**
 * Builds a default Hoplite-based [ConfigBinder], reading properties in the following order, from highest priority
 * to lowest:
 * - environment variables (supports upper case, convert `_` to `.`; see [isKnownNonConfigEnvVar] for exclusions)
 * - system properties (with `config.override.` prefix)
 * - `application-local.conf` in resources
 * - `application.conf` in resources
 *
 * The stage 1, 2, and 3 hooks give more control over the Hoplite [ConfigLoaderBuilder] with the following
 * priorities:
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
 * If report is true (the default), a report of all config keys and values is enabled. All sensitive values should
 * be of type `Secret` to avoid leaking them in the report, named with "secret", "credential", or "pass" in the
 * name, or have pre-processor metadata that identifies the value as a secret. Configure this via [secretsPolicy].
 *
 * [environmentVariables] supplies the environment variables to read (defaults to [System.getenv]).
 *
 * Returns a plain [ConfigBinder]; decode config sections with `binder.config<T>("key")`:
 *
 * ```
 * val binder = hopliteConfigBinder(withConfigStage1 = { /* ... */ })
 * val serverConfig = binder.config<ServerConfig>("server")
 * ```
 */
fun hopliteConfigBinder(
  withConfigStage1: ConfigLoaderBuilder.() -> ConfigLoaderBuilder = { this },
  withConfigStage2: ConfigLoaderBuilder.() -> ConfigLoaderBuilder = { this },
  withConfigStage3: ConfigLoaderBuilder.() -> ConfigLoaderBuilder = { this },
  decoders: Iterable<Decoder<*>> = emptyList(),
  secretsPolicy: SecretsPolicy = StandardSecretsPolicy,
  obfuscator: Obfuscator = StrictObfuscator(),
  report: Boolean = true,
  strict: Boolean = false,
  environmentVariables: () -> Map<String, String> = { System.getenv() },
): ConfigBinder {
  @OptIn(ExperimentalHoplite::class)
  val upstream = ConfigLoaderBuilder.empty()
    .addDefaultDecoders()
    .addDecoders(decoders)
    .addDefaultPreprocessors()
    .addDefaultNodeTransformers()
    .addDefaultParamMappers()
    .withConfigStage1()
    .addPropertySource(EnvironmentVariablesPropertySource({
      environmentVariables().filterKeys { !isKnownNonConfigEnvVar(it) }
    }))
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

  return HopliteConfigBinder(upstream)
}
