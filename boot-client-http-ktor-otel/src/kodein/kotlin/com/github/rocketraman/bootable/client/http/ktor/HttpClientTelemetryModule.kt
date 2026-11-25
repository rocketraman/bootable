package com.github.rocketraman.bootable.client.http.ktor

import com.github.rocketraman.bootable.config.bindConfig
import org.kodein.di.DI
import org.kodein.di.bindSet
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that traces and measures every HTTP client's requests, through the OpenTelemetry of
 * `openTelemetryModule` (from boot-telemetry-otel-kodein): it adds [HttpClientTelemetry], with its
 * [HttpClientTelemetryConfig] decoded from the `telemetry.httpClient` config section, to the customizers of
 * `ktorClientModule`. Import it after both. Requires a `ConfigBinder` binding (e.g. from boot-config-hoplite's
 * `hopliteConfigModule`).
 *
 * To adjust the Ktor client instrumentation beyond [HttpClientTelemetryConfig], add an
 * [HttpClientTelemetryCustomizer] to the set this module opens.
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val httpClientTelemetryModule by DI.Module {
  bindSet<HttpClientTelemetryCustomizer>()
  bindConfig<HttpClientTelemetryConfig>("telemetry.httpClient")
  bindSingleton {
    val customizers = instance<Set<HttpClientTelemetryCustomizer>>()
    HttpClientTelemetry(instance(), instance()) { builder -> customizers.forEach { it.customize(builder) } }
  }
  inBindSet<KtorClientCustomizer> { add { singleton { instance<HttpClientTelemetry>() } } }
}
