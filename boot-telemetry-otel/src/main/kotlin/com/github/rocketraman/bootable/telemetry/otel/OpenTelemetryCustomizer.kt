package com.github.rocketraman.bootable.telemetry.otel

import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.samplers.Sampler

/**
 * Adjusts the OpenTelemetry SDK as [OpenTelemetryService] builds it, after the typed settings of [TelemetryConfig] are
 * applied. This is how other modules plug into the SDK, e.g. boot-telemetry-otel-prometheus registers its metric
 * reader, and boot-telemetry-otel-gcp adds the GCP resource and authenticates OTLP export to Google. Applications use
 * it for SDK features [TelemetryConfig] doesn't cover.
 *
 * Every method has a default that changes nothing, so implement only what you need. They are called in this order:
 * [customizeResource], [customizeSampler], then, for each OTLP exporter, [exportHeaders] and [customizeSpanExporter] or
 * [customizeMetricExporter], then [customizeTracerProvider] and [customizeMeterProvider].
 */
interface OpenTelemetryCustomizer {
  /** The resource that describes the application, e.g. merged with attributes detected from the platform. */
  fun customizeResource(resource: Resource): Resource = resource

  /** The sampler that decides which traces are recorded, e.g. wrapped to drop some. */
  fun customizeSampler(sampler: Sampler): Sampler = sampler

  /**
   * Headers to send with each OTLP export request to [endpoint], computed for each request, e.g. a fresh access token;
   * null, the default, for none. Called once for each OTLP exporter, as it is built.
   *
   * An OTLP exporter has only one header supplier, so the exporters' own `setHeaders` would leave room for one
   * customizer only. Use this instead: every customizer's headers are sent, and where two give the same header, the
   * later customizer's wins. Fixed headers can still be added with `addHeader`, in [customizeSpanExporter] and
   * [customizeMetricExporter].
   */
  fun exportHeaders(endpoint: String): (() -> Map<String, String>)? = null

  /**
   * The OTLP span exporter, which sends to [endpoint], e.g. to add a fixed header. Don't call its `setHeaders`: it
   * would replace the headers of [exportHeaders].
   */
  fun customizeSpanExporter(builder: OtlpHttpSpanExporterBuilder, endpoint: String) {}

  /**
   * The OTLP metric exporter, which sends to [endpoint], e.g. to add a fixed header. Don't call its `setHeaders`: it
   * would replace the headers of [exportHeaders].
   */
  fun customizeMetricExporter(builder: OtlpHttpMetricExporterBuilder, endpoint: String) {}

  /** The tracer provider, e.g. to add a span processor. Not called when [TracesConfig.enabled] is off. */
  fun customizeTracerProvider(builder: SdkTracerProviderBuilder) {}

  /** The meter provider, e.g. to register a metric reader or a view. Not called when [MetricsConfig.enabled] is off. */
  fun customizeMeterProvider(builder: SdkMeterProviderBuilder) {}
}
