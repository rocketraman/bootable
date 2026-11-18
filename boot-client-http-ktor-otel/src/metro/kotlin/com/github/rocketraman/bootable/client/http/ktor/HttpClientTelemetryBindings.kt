package com.github.rocketraman.bootable.client.http.ktor

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.opentelemetry.api.OpenTelemetry

/**
 * Traces and measures every HTTP client's requests, through the OpenTelemetry of `OpenTelemetryBindings` (from
 * boot-telemetry-otel-metro): it contributes [HttpClientTelemetry], with its [HttpClientTelemetryConfig] decoded from
 * the `telemetry.httpClient` config section, to the customizers of `KtorClientBindings`.
 *
 * To adjust the Ktor client instrumentation beyond [HttpClientTelemetryConfig], contribute an
 * [HttpClientTelemetryCustomizer].
 */
@ContributesTo(BootScope::class)
interface HttpClientTelemetryBindings {
  @Multibinds(allowEmpty = true) val httpClientTelemetryCustomizers: Set<HttpClientTelemetryCustomizer>

  companion object {
    @Provides @SingleIn(BootScope::class)
    fun httpClientTelemetryConfig(binder: ConfigBinder): HttpClientTelemetryConfig = binder.config("telemetry.httpClient")

    @Provides @SingleIn(BootScope::class)
    fun httpClientTelemetry(
      openTelemetry: OpenTelemetry,
      config: HttpClientTelemetryConfig,
      customizers: Set<HttpClientTelemetryCustomizer>,
    ): HttpClientTelemetry =
      HttpClientTelemetry(openTelemetry, config) { builder -> customizers.forEach { it.customize(builder) } }

    @Provides @IntoSet fun httpClientTelemetryCustomizer(telemetry: HttpClientTelemetry): KtorClientCustomizer = telemetry
  }
}
