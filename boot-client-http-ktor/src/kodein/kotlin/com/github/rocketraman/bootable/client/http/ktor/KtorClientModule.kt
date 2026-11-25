package com.github.rocketraman.bootable.client.http.ktor

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.config.bindConfig
import io.ktor.client.*
import org.kodein.di.DI
import org.kodein.di.bindSet
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that binds the application's [KtorClientService], with its [KtorClientsConfig] decoded from the
 * `http.client` config section, and its default [HttpClient], and registers the service into the app-service set, so
 * the clients close when the application stops. Requires a `ConfigBinder` binding (e.g. from boot-config-hoplite's
 * `hopliteConfigModule`), and a [KtorClientEngine], the application's choice of engine:
 *
 * ```
 * bindSingleton<KtorClientEngine> { CioClientEngine() }
 * ```
 *
 * In a test, bind one that returns Ktor's `MockEngine` instead. Take a named client from the service:
 * `instance<KtorClientService>()["github"]`.
 *
 * To adjust the clients beyond [KtorClientConfig], such as to install `ContentNegotiation`, add a
 * [KtorClientCustomizer] to the set this module opens, or import a module that does, such as
 * `ktorClientTelemetryModule` from `boot-client-http-ktor-otel`:
 *
 * ```
 * inBindSet<KtorClientCustomizer> { add { singleton { KtorClientCustomizer { _, config -> config.install(ContentNegotiation) { json() } } } } }
 * ```
 *
 * A module can contribute a named client's default settings, which the application's `http.client` overrides, by
 * adding a [KtorClientDefaults] to the set this module opens.
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val ktorClientModule by DI.Module {
  bindSet<KtorClientCustomizer>()
  bindSet<KtorClientDefaults>()
  bindConfig<KtorClientsConfig>("http.client")
  bindSingleton {
    KtorClientService(
      instance(),
      instance(),
      KtorClientCustomizer.of(instance<Set<KtorClientCustomizer>>()),
      instance<Set<KtorClientDefaults>>(),
    )
  }
  bindSingleton<HttpClient> { instance<KtorClientService>().default }
  inBindSet<AppService> { add { singleton { instance<KtorClientService>() } } }
}
