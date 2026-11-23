package com.github.rocketraman.bootable.client.http.ktor.koin

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.client.http.ktor.KtorClientConfig
import com.github.rocketraman.bootable.client.http.ktor.KtorClientCustomizer
import com.github.rocketraman.bootable.client.http.ktor.KtorClientDefaults
import com.github.rocketraman.bootable.client.http.ktor.KtorClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.client.http.ktor.KtorClientsConfig
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import io.ktor.client.*
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that defines the application's [KtorClientService], with its [KtorClientsConfig] decoded from the
 * `http.client` config section, and its default [HttpClient], and adds the service to the app services, so the
 * clients close when the application stops. Requires a `ConfigBinder` definition (e.g. from boot-config-hoplite's
 * `hopliteConfigModule()`), and a [KtorClientEngine], the application's choice of engine:
 *
 * ```
 * single<KtorClientEngine> { CioClientEngine() }
 * ```
 *
 * In a test, define one that returns Ktor's `MockEngine` instead. Take a named client from the service:
 * `get<KtorClientService>()["github"]`.
 *
 * To adjust the clients beyond [KtorClientConfig], such as to install `ContentNegotiation`, define a
 * [KtorClientCustomizer], with a qualifier of its own, or include a module that does, such as
 * `httpClientTelemetryModule()` from `boot-client-http-ktor-otel`:
 *
 * ```
 * single<KtorClientCustomizer>(named("json")) { KtorClientCustomizer { _, config -> config.install(ContentNegotiation) { json() } } }
 * ```
 *
 * A module can contribute a named client's default settings, which the application's `http.client` overrides, by
 * defining a [KtorClientDefaults], in the same way.
 */
fun ktorClientModule(): Module = module {
  single<KtorClientsConfig> { get<ConfigBinder>().config("http.client") }
  single {
    KtorClientService(
      get(),
      get(),
      KtorClientCustomizer.of(getAll<KtorClientCustomizer>()),
      getAll<KtorClientDefaults>(),
    )
  }
  single<HttpClient> { get<KtorClientService>().default }
  single<AppService>(named<KtorClientService>()) { get<KtorClientService>() }
}
