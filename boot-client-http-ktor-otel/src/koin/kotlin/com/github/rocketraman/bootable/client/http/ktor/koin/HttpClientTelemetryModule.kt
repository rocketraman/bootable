package com.github.rocketraman.bootable.client.http.ktor.koin

import com.github.rocketraman.bootable.client.http.ktor.HttpClientTelemetry
import com.github.rocketraman.bootable.client.http.ktor.HttpClientTelemetryConfig
import com.github.rocketraman.bootable.client.http.ktor.HttpClientTelemetryCustomizer
import com.github.rocketraman.bootable.client.http.ktor.KtorClientCustomizer
import com.github.rocketraman.bootable.config.koin.config
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that traces and measures every HTTP client's requests, through the OpenTelemetry of
 * `openTelemetryModule()` (from boot-telemetry-otel-koin): it adds [HttpClientTelemetry], with its
 * [HttpClientTelemetryConfig] decoded from the `telemetry.httpClient` config section, to the customizers of
 * `ktorClientModule()`. Requires a `ConfigBinder` definition (e.g. from boot-config-hoplite's `hopliteConfigModule()`).
 *
 * To adjust the Ktor client instrumentation beyond [HttpClientTelemetryConfig], define an
 * [HttpClientTelemetryCustomizer], with a qualifier of its own.
 */
fun httpClientTelemetryModule(): Module = module {
  config<HttpClientTelemetryConfig>("telemetry.httpClient")
  single {
    val customizers = getAll<HttpClientTelemetryCustomizer>()
    HttpClientTelemetry(get(), get()) { builder -> customizers.forEach { it.customize(builder) } }
  }
  single<KtorClientCustomizer>(named<HttpClientTelemetry>()) { get<HttpClientTelemetry>() }
}
