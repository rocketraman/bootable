package com.github.rocketraman.bootable.test

import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor

/**
 * Collects every span and metric an OpenTelemetry SDK records, in memory, for a test to assert on: an
 * [OpenTelemetryCustomizer], so pass it to the `OpenTelemetryService` the code under test records through, or add it
 * to the SDK's customizers with `inMemoryTelemetryModule` (Koin, Kodein), `InMemoryTelemetryBindings` (Metro), or
 * `InMemoryTelemetryModule` (Inara).
 *
 * Spans are exported as each ends, to [spanExporter], OpenTelemetry's own in-memory exporter, and metrics are read on
 * demand by [metricReader], its in-memory reader. The SDK's other exporters, such as OTLP, still get them too.
 */
class InMemoryTelemetry : OpenTelemetryCustomizer {
  val spanExporter: InMemorySpanExporter = InMemorySpanExporter.create()
  val metricReader: InMemoryMetricReader = InMemoryMetricReader.create()

  /** The spans that have ended so far, in the order they ended. */
  val spans: List<SpanData> get() = spanExporter.finishedSpanItems

  /** The spans named [name] that have ended so far. */
  fun spans(name: String): List<SpanData> = spans.filter { it.name == name }

  /** Every metric recorded so far, collected now. */
  fun metrics(): Collection<MetricData> = metricReader.collectAllMetrics()

  /** The metric named [name], collected now, or null if nothing has recorded it. */
  fun metric(name: String): MetricData? = metrics().firstOrNull { it.name == name }

  /** Forgets the spans ended so far, e.g. those of the application's start, so a test sees only its own. */
  fun resetSpans() = spanExporter.reset()

  override fun customizeTracerProvider(builder: SdkTracerProviderBuilder) {
    builder.addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
  }

  override fun customizeMeterProvider(builder: SdkMeterProviderBuilder) {
    builder.registerMetricReader(metricReader)
  }
}
