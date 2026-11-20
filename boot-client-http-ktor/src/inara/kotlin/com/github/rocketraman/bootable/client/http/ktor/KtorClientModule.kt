@file:JvmName("KtorClientModuleInara")

package com.github.rocketraman.bootable.client.http.ktor

import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.config
import io.ktor.client.*
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.inara.DISet

/** Marks a module member as one of the [KtorClientCustomizer]s of the application's HTTP clients. */
@DISet(of = KtorClientCustomizer::class)
annotation class KtorClientCustomizers

/** Marks a module member as one of the [KtorClientDefaults], a named client's default settings. */
@DISet(of = KtorClientDefaults::class)
annotation class NamedClientDefaults

/**
 * The Inara module that provides the application's [KtorClientService], with its [KtorClientsConfig] decoded from the
 * `http.client` config section of the [ConfigModule] it requires, and its default [HttpClient]. The service is one of
 * the application's services, so the clients close when the application stops. Take a named client from the
 * service: `ktorClientService["github"]`.
 *
 * To adjust the clients beyond [KtorClientConfig], such as to install `ContentNegotiation`, mark a member
 * [KtorClientCustomizers], or compose a module that does, such as `httpClientTelemetryModule()` from
 * `boot-client-http-ktor-otel`. A module can contribute a named client's default settings, which the application's
 * `http.client` overrides, with a member marked [NamedClientDefaults].
 */
@DIModule(sets = [KtorClientCustomizers::class, NamedClientDefaults::class])
interface KtorClientModule : @DIModule.Required ConfigModule {
  val ktorClientsConfig: KtorClientsConfig
  val ktorClientEngine: KtorClientEngine
  @AppServices val ktorClientService: KtorClientService
  val httpClient: HttpClient get() = ktorClientService.default
}

/** A [KtorClientModule] on [engine], the application's choice, e.g. `CioClientEngine()`, or Ktor's `MockEngine` in a test. */
fun ktorClientModule(engine: KtorClientEngine): KtorClientModulePart = KtorClientModulePart(
  ktorClientsConfig = config("http.client"),
  ktorClientEngine = Binding.value(engine),
  ktorClientService = Binding.single {
    KtorClientService(
      ktorClientsConfig,
      ktorClientEngine,
      KtorClientCustomizer.of(setOfKtorClientCustomizers()),
      setOfNamedClientDefaults(),
    )
  },
)
