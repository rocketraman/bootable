package com.github.rocketraman.bootable.client.http.ktor

import io.ktor.client.*

/**
 * Adjusts each client's Ktor configuration after the typed settings are applied: to install a plugin, such as
 * `ContentNegotiation`, or to set what [KtorClientConfig] doesn't cover. [name] is the client's name, or
 * [KtorClientsConfig.DEFAULT], so a customizer can apply to only some clients:
 *
 * ```
 * KtorClientCustomizer { name, config ->
 *   config.install(ContentNegotiation) { json() }
 *   if (name == "github") config.expectSuccess = true
 * }
 * ```
 */
fun interface KtorClientCustomizer {
  fun customize(name: String, config: HttpClientConfig<*>)

  companion object {
    /** Does nothing. */
    val None = KtorClientCustomizer { _, _ -> }

    /** Runs each of [customizers] in turn, e.g. a DI set of them. */
    fun of(customizers: Iterable<KtorClientCustomizer>) =
      KtorClientCustomizer { name, config -> customizers.forEach { it.customize(name, config) } }
  }
}
