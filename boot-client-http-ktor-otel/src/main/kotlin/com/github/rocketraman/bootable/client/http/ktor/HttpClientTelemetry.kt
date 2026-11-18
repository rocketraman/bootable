package com.github.rocketraman.bootable.client.http.ktor

import io.ktor.client.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.KtorClientTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.KtorClientTelemetryBuilder

/**
 * How the HTTP clients report their telemetry. Every field is optional.
 *
 * Decoded from the `telemetry.httpClient` config section:
 *
 * ```
 * telemetry {
 *   httpClient {
 *     capturedResponseHeaders = [X-Request-Id]
 *   }
 * }
 * ```
 */
data class HttpClientTelemetryConfig(
  /** Request headers to record on each request's span, as `http.request.header.<name>`. */
  val capturedRequestHeaders: List<String> = emptyList(),
  /** Response headers to record on each request's span, as `http.response.header.<name>`. */
  val capturedResponseHeaders: List<String> = emptyList(),
)

/**
 * Adjusts OpenTelemetry's Ktor client instrumentation after the typed settings are applied, for settings
 * [HttpClientTelemetryConfig] doesn't cover, such as extra span attributes, or a span name of its own.
 */
fun interface HttpClientTelemetryCustomizer {
  fun customize(builder: KtorClientTelemetryBuilder)
}

/**
 * The HTTP clients' telemetry: a [KtorClientCustomizer] that installs OpenTelemetry's Ktor client instrumentation in
 * every client. Each request is a client span, a child of the span current where the call is made, such as a Ktor
 * server request's, and carries its trace to the server it calls, in a `traceparent` header. Each also records the
 * standard HTTP client metrics, such as `http.client.request.duration`.
 *
 * Add it to `KtorClientService`'s customizers:
 *
 * ```
 * KtorClientService(binder.config("http.client"), CioClientEngine(), HttpClientTelemetry(telemetry.openTelemetry))
 * ```
 *
 * A request that `HttpRequestRetry` retries is a span for each attempt.
 */
class HttpClientTelemetry(
  val openTelemetry: OpenTelemetry,
  val config: HttpClientTelemetryConfig = HttpClientTelemetryConfig(),
  private val customizer: HttpClientTelemetryCustomizer = HttpClientTelemetryCustomizer { },
) : KtorClientCustomizer {
  override fun customize(name: String, config: HttpClientConfig<*>) {
    config.install(KtorClientTelemetry) {
      setOpenTelemetry(this@HttpClientTelemetry.openTelemetry)
      capturedRequestHeaders(this@HttpClientTelemetry.config.capturedRequestHeaders)
      capturedResponseHeaders(this@HttpClientTelemetry.config.capturedResponseHeaders)
      customizer.customize(this)
    }
  }
}
