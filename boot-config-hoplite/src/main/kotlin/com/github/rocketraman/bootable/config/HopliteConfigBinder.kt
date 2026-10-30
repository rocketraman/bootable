package com.github.rocketraman.bootable.config

import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.ConfigBinder as UpstreamConfigBinder
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.fp.valid
import com.sksamuel.hoplite.resolver.context.ContextResolver
import com.sksamuel.hoplite.resolver.context.EnvVarContextResolver
import com.sksamuel.hoplite.secrets.CompositeSecretsPolicy
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
 * Builds a default Hoplite-based [ConfigBinder], reading properties in the following order, from highest priority
 * to lowest:
 * - environment variables (supports upper case, convert `_` to `.`)
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
 * be of type [Masked] (or Hoplite's `Secret`), or named with "secret", "credential", or "pass" in the name, to
 * avoid leaking them in the report. Configure this via [secretsPolicy]; a [Masked] value is masked whatever the
 * policy. The report shows values as loaded, before resolvers run, so it shows `${{ env:DB_PASSWORD }}`, not the
 * password.
 *
 * Config values may reference other values with Hoplite's resolvers, e.g. `${{ env:DB_HOST }}`,
 * `${{ env:DB_HOST:-localhost }}`, or `${{ sysprop:name }}`. These resolve only in a field whose value is a single
 * string, not in a map, list, or set; use HOCON's own `${?DB_HOST}` substitutions there.
 *
 * [environmentVariables] supplies the environment variables to read, both as a config source and for
 * `${{ env:name }}` (defaults to [System.getenv]).
 *
 * A sealed type is chosen by its subtype's name, in a `_type` key next to the subtype's properties, e.g.
 * `tiebreaker { _type = Shuffled, seed = 42 }`, or, in the environment, as the sealed type's own value, e.g.
 * `TIEBREAKER=Shuffled` and `TIEBREAKER_SEED=42`.
 *
 * Enum values and sealed subtype names match in any case, so `fail`, `Fail`, and `FAIL` are all `Fail`.
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
    .addDecoder(MaskedDecoder())
    .addDecoders(decoders)
    .addResolvers(defaultResolvers().map {
      if (it == EnvVarContextResolver) EnvironmentVariablesContextResolver(environmentVariables) else it
    })
    .addDefaultNodeTransformers()
    .addDefaultParamMappers()
    .withConfigStage1()
    .addPropertySource(EnvironmentVariablesPropertySource(environmentVariables))
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
        withSecretsPolicy(CompositeSecretsPolicy(setOf(MaskedSecretsPolicy, secretsPolicy)))
        withObfuscator(obfuscator)
      }
      if (strict) {
        strict()
      }
    }
    .withExplicitSealedTypes()
    .withResolveTypesCaseInsensitive()
    .build()
    .configBinder()

  return HopliteConfigBinder(upstream)
}

/**
 * Hoplite's [EnvVarContextResolver], reading [environmentVariables] instead of [System.getenv], so that
 * `${{ env:name }}` sees the same environment as the environment variables source.
 */
private class EnvironmentVariablesContextResolver(
  private val environmentVariables: () -> Map<String, String>,
) : ContextResolver() {
  override val contextKey = EnvVarContextResolver.contextKey
  override val default = EnvVarContextResolver.default

  override fun lookup(path: String, node: StringNode, root: Node, context: DecoderContext): ConfigResult<String?> =
    environmentVariables()[path].valid()
}
