package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.config.Masked
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector
import io.opentelemetry.sdk.resources.Resource
import java.net.URI

/**
 * SigNoz settings. Every field is optional.
 *
 * Decoded from the `telemetry.signoz` config section:
 *
 * ```
 * telemetry {
 *   sdk {
 *     # SigNoz Cloud, in its us, eu, or in region, or a self-hosted SigNoz's collector, such as
 *     # http://signoz-otel-collector:4318
 *     otlp { endpoint = "https://ingest.us.signoz.cloud:443" }
 *   }
 *   signoz {
 *     ingestionKey = ${?SIGNOZ_INGESTION_KEY}
 *   }
 * }
 * ```
 */
data class SigNozTelemetryConfig(
  /**
   * The SigNoz Cloud ingestion key, from Settings > Ingestion, sent as the `signoz-ingestion-key` header. A
   * self-hosted SigNoz needs none.
   */
  val ingestionKey: Masked? = null,
  /**
   * Whether to send [ingestionKey] with OTLP export. Unset, it is sent when export goes to SigNoz Cloud, i.e. to a
   * `signoz.cloud` endpoint, and not when it goes elsewhere, so a key set for every environment stays off a local
   * collector.
   */
  val authenticate: Boolean? = null,
  /**
   * The temporality of exported counters and histograms. [MetricsTemporality.Delta] exports only what changed since the
   * last export, which SigNoz suggests to cut the samples it stores, but an export that fails is lost for good, where a
   * cumulative one is made up by the next. Up-down counters and gauges are always cumulative.
   */
  val temporality: MetricsTemporality = MetricsTemporality.Cumulative,
  /**
   * Whether to also set the resource's `deployment.environment`, from `deployment.environment.name`, which is where
   * current OpenTelemetry puts the environment; SigNoz's services and environment filters read the older attribute.
   */
  val legacyEnvironmentAttribute: Boolean = true,
)

enum class MetricsTemporality { Cumulative, Delta }

/**
 * Connects the application's telemetry to SigNoz, which ingests OTLP directly: SigNoz Cloud at
 * `https://ingest.<region>.signoz.cloud:443`, or a self-hosted SigNoz's collector on port 4318. Point
 * `telemetry.sdk.otlp.endpoint` there, and add this to `OpenTelemetryService`'s customizers:
 *
 * ```
 * val telemetry = OpenTelemetryService(telemetryConfig(binder), listOf(SigNozTelemetry(binder.config("telemetry.signoz"))))
 * ```
 *
 * It authenticates export to SigNoz Cloud with the ingestion key, exports counters and histograms with the configured
 * temporality, and sets the resource's environment where SigNoz looks for it.
 */
class SigNozTelemetry(private val config: SigNozTelemetryConfig = SigNozTelemetryConfig()) : OpenTelemetryCustomizer {

  override fun customizeResource(resource: Resource): Resource {
    if (!config.legacyEnvironmentAttribute || resource.getAttribute(DEPLOYMENT_ENVIRONMENT) != null) return resource
    val environment = resource.getAttribute(DEPLOYMENT_ENVIRONMENT_NAME) ?: return resource
    return resource.merge(Resource.create(Attributes.of(DEPLOYMENT_ENVIRONMENT, environment)))
  }

  override fun customizeSpanExporter(builder: OtlpHttpSpanExporterBuilder, endpoint: String) {
    if (authenticates(endpoint)) builder.addHeader(INGESTION_KEY_HEADER, ingestionKey())
  }

  override fun customizeMetricExporter(builder: OtlpHttpMetricExporterBuilder, endpoint: String) {
    if (authenticates(endpoint)) builder.addHeader(INGESTION_KEY_HEADER, ingestionKey())
    if (config.temporality == MetricsTemporality.Delta) {
      // delta for counters and histograms; up-down counters stay cumulative, as their value is the total
      builder.setAggregationTemporalitySelector(AggregationTemporalitySelector.deltaPreferred())
    }
  }

  /** Whether export to [endpoint] carries the ingestion key. */
  fun authenticates(endpoint: String): Boolean =
    config.authenticate ?: URI(endpoint).host.orEmpty().let { it == SIGNOZ_CLOUD || it.endsWith(".$SIGNOZ_CLOUD") }

  // fails when the SDK is built, so a missing key stops the application at boot rather than failing every export
  private fun ingestionKey(): String = checkNotNull(config.ingestionKey) {
    "OTLP export to SigNoz Cloud needs an ingestion key, telemetry.signoz.ingestionKey, but there is none"
  }.value

  override fun toString() = "SigNozTelemetry($config)"

  private companion object {
    const val SIGNOZ_CLOUD = "signoz.cloud"
    const val INGESTION_KEY_HEADER = "signoz-ingestion-key"
    val DEPLOYMENT_ENVIRONMENT: AttributeKey<String> = AttributeKey.stringKey("deployment.environment")
    val DEPLOYMENT_ENVIRONMENT_NAME: AttributeKey<String> = AttributeKey.stringKey("deployment.environment.name")
  }
}
