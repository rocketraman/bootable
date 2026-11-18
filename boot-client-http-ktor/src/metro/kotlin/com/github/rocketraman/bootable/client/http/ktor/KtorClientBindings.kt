package com.github.rocketraman.bootable.client.http.ktor

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.*

/**
 * Provides the application's [KtorClientService], with its [KtorClientsConfig] decoded from the `http.client` config
 * section, and its default [HttpClient], and registers the service into the app-service set, so the clients close
 * when the application stops.
 *
 * It needs a [KtorClientEngine], the application's choice of engine; without one, the graph fails to compile with a
 * missing binding:
 *
 * ```
 * @Provides fun ktorClientEngine(): KtorClientEngine = CioClientEngine()
 * ```
 *
 * In a test, provide one that returns Ktor's `MockEngine` instead. Take a named client from the service:
 * `clients["github"]`.
 *
 * To adjust the clients beyond [KtorClientConfig], such as to install `ContentNegotiation`, contribute a
 * [KtorClientCustomizer], or depend on a module that does, such as `boot-client-http-ktor-otel-metro`:
 *
 * ```
 * @Provides @IntoSet fun json(): KtorClientCustomizer =
 *   KtorClientCustomizer { _, config -> config.install(ContentNegotiation) { json() } }
 * ```
 *
 * A module can contribute a named client's default settings, which the application's `http.client` overrides, by
 * contributing a [KtorClientDefaults].
 */
@ContributesTo(BootScope::class)
interface KtorClientBindings {
  @Multibinds(allowEmpty = true) val ktorClientCustomizers: Set<KtorClientCustomizer>

  @Multibinds(allowEmpty = true) val ktorClientDefaults: Set<KtorClientDefaults>

  companion object {
    @Provides @SingleIn(BootScope::class)
    fun ktorClientsConfig(binder: ConfigBinder): KtorClientsConfig = binder.config("http.client")

    @Provides @SingleIn(BootScope::class)
    fun ktorClientService(
      config: KtorClientsConfig,
      engine: KtorClientEngine,
      customizers: Set<KtorClientCustomizer>,
      defaults: Set<KtorClientDefaults>,
    ): KtorClientService = KtorClientService(config, engine, KtorClientCustomizer.of(customizers), defaults)

    @Provides fun httpClient(service: KtorClientService): HttpClient = service.default

    @Provides @IntoSet fun ktorClientAppService(service: KtorClientService): AppService = service
  }
}
