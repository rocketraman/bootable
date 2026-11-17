package com.github.rocketraman.bootable.telemetry.otel

import io.opentelemetry.exporter.prometheus.PrometheusMetricReader
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.prometheus.metrics.expositionformats.OpenMetricsTextFormatWriter
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter
import java.io.ByteArrayOutputStream

/**
 * Prometheus export settings. Every field is optional; an unset field keeps the OpenTelemetry Prometheus exporter's
 * default.
 *
 * Decoded from the `telemetry.prometheus` config section:
 *
 * ```
 * telemetry {
 *   prometheus {
 *     resourceAttributesAsLabels = [service.name, deployment.environment.name]
 *   }
 * }
 * ```
 */
data class PrometheusTelemetryConfig(
  /** Whether each series is labelled with the instrumentation scope that recorded it, `otel_scope_name` and so on. */
  val otelScopeLabels: Boolean? = null,
  /** Whether the resource is exported as the `target_info` metric, for joining against in queries. */
  val targetInfo: Boolean? = null,
  /**
   * Resource attributes to copy onto every series as labels, such as `service.name`. Prometheus normally identifies
   * the target by its own `job` and `instance` labels instead.
   */
  val resourceAttributesAsLabels: Set<String> = emptySet(),
)

/**
 * Exposes the application's metrics for Prometheus to scrape. Add it to `OpenTelemetryService`'s customizers, which
 * registers its metric reader with the SDK, and serve it, as a [MetricsSnapshotProvider], from an HTTP integration such as
 * boot-server-http-ktor-otel:
 *
 * ```
 * val prometheus = PrometheusTelemetry(binder.config("telemetry.prometheus"))
 * val telemetry = OpenTelemetryService(telemetryConfig(binder), listOf(prometheus))
 * ```
 *
 * A scrape is answered in the OpenMetrics text format when the scraper accepts it, as Prometheus does, and otherwise
 * in the Prometheus text format, which every scraper understands. It is never Prometheus's protobuf format, so the
 * body is always text.
 *
 * Exemplars, in the OpenMetrics format, carry only the trace and span ids of the measurement they sample, so they link
 * to its trace: not the measurement's other attributes, which would often exceed OpenMetrics's 128-character limit for
 * an exemplar's labels.
 */
class PrometheusTelemetry(config: PrometheusTelemetryConfig = PrometheusTelemetryConfig()) :
  OpenTelemetryCustomizer, MetricsSnapshotProvider {

  /** The SDK's metric reader that the scrapes collect from. */
  val reader: PrometheusMetricReader = PrometheusMetricReader.builder().apply {
    config.otelScopeLabels?.let { setOtelScopeLabelsEnabled(it) }
    config.targetInfo?.let { setTargetInfoMetricEnabled(it) }
    if (config.resourceAttributesAsLabels.isNotEmpty()) {
      setAllowedResourceAttributesFilter { it in config.resourceAttributesAsLabels }
    }
  }.build()

  override fun customizeMeterProvider(builder: SdkMeterProviderBuilder) {
    builder.registerMetricReader(ExemplarTrimmingReader(reader))
  }

  override fun snapshot(accept: String?): MetricsSnapshot {
    val writer = if (accept?.contains(OPENMETRICS_MEDIA_TYPE) == true) openMetrics else text
    val body = ByteArrayOutputStream().also { writer.write(it, reader.collect()) }.toByteArray()
    return MetricsSnapshot(writer.contentType, body)
  }

  override fun toString() = "PrometheusTelemetry"

  private companion object {
    const val OPENMETRICS_MEDIA_TYPE = "application/openmetrics-text"
    val openMetrics: OpenMetricsTextFormatWriter = OpenMetricsTextFormatWriter.create()
    val text: PrometheusTextFormatWriter = PrometheusTextFormatWriter.create()
  }
}
