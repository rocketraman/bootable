package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.Masked
import com.github.rocketraman.bootable.config.config
import java.net.URLDecoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Settings for the application's OpenTelemetry SDK, owned by [OpenTelemetryService]: what the application is, where
 * its telemetry goes, and which signals it produces. Each other telemetry module (Prometheus, GCP, Ktor) reads its own
 * settings from its own subsection of `telemetry`.
 *
 * Decoded from the `telemetry.sdk` config section:
 *
 * ```
 * telemetry {
 *   sdk {
 *     serviceName = my-service
 *     otlp { endpoint = "http://otel-collector:4318" }
 *     traces { sampleRatio = 0.1 }
 *   }
 *   prometheus { ... }  # read by boot-telemetry-otel-prometheus
 * }
 * ```
 *
 * [telemetryConfig] fills in what this leaves unset from the standard OpenTelemetry environment variables; see
 * [withOtelEnvironment]. Anything else goes through an [OpenTelemetryCustomizer].
 */
data class TelemetryConfig(
  /** The resource's `service.name`, which names the application to every backend. */
  val serviceName: String? = null,
  /** The resource's `service.version`. */
  val serviceVersion: String? = null,
  /**
   * The resource's `service.instance.id`, which tells this instance apart from the service's others. Unset, each
   * start gets a random one. Set it where the platform has a stable name for the instance, such as the pod name,
   * through the Kubernetes downward API, which a StatefulSet keeps across restarts.
   */
  val serviceInstanceId: String? = null,
  /**
   * The resource's `deployment.environment.name`. [telemetryConfig] defaults it to the `env.environment` config key,
   * which is the one boot-env reads, so an application using boot-env gets it without repeating it.
   */
  val environment: String? = null,
  /** More resource attributes, which describe the application to the backend, e.g. `service.namespace`. */
  val resourceAttributes: Map<String, String> = emptyMap(),
  /** Where OTLP export goes. Without an endpoint there is no OTLP export, so a local run sends nothing anywhere. */
  val otlp: OtlpConfig = OtlpConfig(),
  val traces: TracesConfig = TracesConfig(),
  val metrics: MetricsConfig = MetricsConfig(),
  /**
   * Whether to register the SDK as the process's `GlobalOpenTelemetry`, for libraries that look it up there rather
   * than being handed it.
   */
  val registerGlobal: Boolean = true,
  /** How long shutdown waits for the last spans and metrics to be exported. */
  val shutdownTimeout: Duration = 10.seconds,
)

/** OTLP export, over HTTP with protobuf encoding. */
data class OtlpConfig(
  /**
   * The base URL of the OTLP receiver, such as an OpenTelemetry collector at `http://localhost:4318`. Each signal is
   * sent to its own path under it: `/v1/traces` and `/v1/metrics`.
   */
  val endpoint: String? = null,
  /** Headers on every export request, such as an ingestion key: [Masked], since they usually carry one. */
  val headers: Map<String, Masked> = emptyMap(),
  /** Whether to gzip each export request. */
  val compression: Boolean = false,
  /** How long to wait for each export request. The SDK's default is 10 seconds. */
  val timeout: Duration? = null,
)

data class TracesConfig(
  /** Whether to record spans at all. Off, no span is recorded or exported. */
  val enabled: Boolean = true,
  /** Whether to export spans over OTLP, when there is an [OtlpConfig.endpoint]. */
  val otlp: Boolean = true,
  /**
   * The fraction of traces to record, from 0 to 1. A trace started elsewhere keeps the caller's decision, so a trace
   * is either recorded in every service it crosses or in none.
   */
  val sampleRatio: Double = 1.0,
)

data class MetricsConfig(
  /** Whether to record metrics at all. Off, no metric is recorded or exported. */
  val enabled: Boolean = true,
  /** Whether to export metrics over OTLP, when there is an [OtlpConfig.endpoint]. */
  val otlp: Boolean = true,
  /** How often metrics are exported over OTLP. */
  val interval: Duration = 60.seconds,
  /** Whether to record the JVM's runtime metrics: memory, garbage collection, threads, classes, and CPU. */
  val runtime: Boolean = true,
)

/** The `env` config section as boot-env reads it, without depending on boot-env. */
internal data class EnvironmentSection(val environment: String? = null)

/**
 * Decodes [TelemetryConfig] from the `telemetry.sdk` config section. When that doesn't set an `environment`, it is
 * taken from `env.environment`, the key boot-env reads, if it is set. The standard OpenTelemetry environment
 * variables, in [env], fill in what is still unset; see [withOtelEnvironment].
 */
fun telemetryConfig(binder: ConfigBinder, env: Map<String, String> = System.getenv()): TelemetryConfig {
  val config = binder.config<TelemetryConfig>("telemetry.sdk")
  val withEnvironment =
    if (config.environment != null) config
    else config.copy(environment = binder.config<EnvironmentSection>("env").environment)
  return withEnvironment.withOtelEnvironment(env)
}

/**
 * Fills in what this config leaves unset from the standard OpenTelemetry environment variables in [env]:
 * - `OTEL_SERVICE_NAME` sets [TelemetryConfig.serviceName], if it is unset; as the typed setting, it then wins over a
 *   `service.name` in [TelemetryConfig.resourceAttributes].
 * - `OTEL_RESOURCE_ATTRIBUTES` is merged into [TelemetryConfig.resourceAttributes], whose values win for a key in both.
 * - `OTEL_EXPORTER_OTLP_ENDPOINT` sets [OtlpConfig.endpoint], if it is unset.
 * - `OTEL_EXPORTER_OTLP_HEADERS` is merged into [OtlpConfig.headers], whose values win for a header in both.
 *
 * No other `OTEL_*` variable is read.
 * A variable that is empty, or only whitespace, is treated as unset, as the OpenTelemetry specification says.
 *
 * These are the names platforms and tools set, such as the OpenTelemetry operator for Kubernetes, and other
 * OpenTelemetry SDKs read, so an application configured for them works unchanged. They also take the
 * `key1=value1,key2=value2` form, which suits resource attributes, whose keys are dotted, better than one
 * environment variable per key.
 */
fun TelemetryConfig.withOtelEnvironment(env: Map<String, String> = System.getenv()): TelemetryConfig {
  fun variable(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
  return copy(
    serviceName = serviceName ?: variable("OTEL_SERVICE_NAME"),
    resourceAttributes = parseKeyValues(variable("OTEL_RESOURCE_ATTRIBUTES")) + resourceAttributes,
    otlp = otlp.copy(
      endpoint = otlp.endpoint ?: variable("OTEL_EXPORTER_OTLP_ENDPOINT"),
      headers = parseKeyValues(variable("OTEL_EXPORTER_OTLP_HEADERS")).mapValues { (_, value) -> Masked(value) } + otlp.headers,
    ),
  )
}

/** Parses the `key1=value1,key2=value2` format of the OpenTelemetry environment variables, whose values may be URL-encoded. */
internal fun parseKeyValues(value: String?): Map<String, String> =
  value.orEmpty().split(',').mapNotNull { pair ->
    val separator = pair.indexOf('=')
    if (separator <= 0) null
    else pair.substring(0, separator).trim() to URLDecoder.decode(pair.substring(separator + 1).trim(), Charsets.UTF_8)
  }.toMap()
