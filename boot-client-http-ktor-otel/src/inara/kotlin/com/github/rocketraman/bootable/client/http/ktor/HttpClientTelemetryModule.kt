@file:JvmName("HttpClientTelemetryModuleInara")

package com.github.rocketraman.bootable.client.http.ktor

import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryApiModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.inara.DISet

/** Marks a module member as one of the [HttpClientTelemetryCustomizer]s of the Ktor client instrumentation. */
@DISet(of = HttpClientTelemetryCustomizer::class)
annotation class HttpClientTelemetryCustomizers

/**
 * The Inara module that traces and measures every HTTP client's requests, through the OpenTelemetry of the
 * [OpenTelemetryApiModule] it requires: it adds [HttpClientTelemetry], with its [HttpClientTelemetryConfig] decoded from
 * the `telemetry.httpClient` config section, to the customizers of the [KtorClientModule] it requires.
 *
 * To adjust the Ktor client instrumentation beyond [HttpClientTelemetryConfig], mark a member
 * [HttpClientTelemetryCustomizers].
 */
@DIModule(sets = [HttpClientTelemetryCustomizers::class])
interface HttpClientTelemetryModule : @DIModule.Required KtorClientModule, @DIModule.Required OpenTelemetryApiModule {
  val httpClientTelemetryConfig: HttpClientTelemetryConfig
  @KtorClientCustomizers val httpClientTelemetry: HttpClientTelemetry
}

/** An [HttpClientTelemetryModule]. */
fun httpClientTelemetryModule(): HttpClientTelemetryModulePart = HttpClientTelemetryModulePart(
  httpClientTelemetryConfig = config("telemetry.httpClient"),
  httpClientTelemetry = Binding.single {
    val customizers = setOfHttpClientTelemetryCustomizers()
    HttpClientTelemetry(openTelemetry, httpClientTelemetryConfig) { builder -> customizers.forEach { it.customize(builder) } }
  },
)
