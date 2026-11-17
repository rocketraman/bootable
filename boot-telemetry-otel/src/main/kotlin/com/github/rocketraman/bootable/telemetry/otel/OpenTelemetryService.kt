package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Ordering
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.samplers.Sampler
import org.apache.logging.log4j.kotlin.logger
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.toJavaDuration

/**
 * Owns the application's OpenTelemetry SDK and ties it to the application lifecycle.
 *
 * The SDK is built when this is constructed, from [config] and then the [customizers], so [openTelemetry] can be
 * handed to instrumentation right away. Starting records the JVM's runtime metrics, when [MetricsConfig.runtime] is
 * on. Shutdown exports the spans and metrics still buffered, then shuts the SDK down, so the last interval of
 * telemetry, usually the most interesting one, isn't lost.
 *
 * When the OpenTelemetry Java agent is attached, it has already built and registered the process's SDK, and owns its
 * lifecycle; [openTelemetry] is then the agent's, and [config] and the [customizers] are ignored.
 *
 * The service is in the [DefaultPhase.Telemetry] phase, so every other service, the database pool included, starts
 * after the SDK and shuts down before it, and its last spans and measurements are in the final export.
 */
class OpenTelemetryService(
  private val config: TelemetryConfig = TelemetryConfig(),
  customizers: Collection<OpenTelemetryCustomizer> = emptyList(),
) : AppService {

  // the agent has already built and registered the process's SDK
  private val agentAttached = isJavaAgentAttached()

  init {
    if (agentAttached) {
      log.info { "The OpenTelemetry Java agent is attached; using its OpenTelemetry instead of building one" }
      if (customizers.isNotEmpty()) log.warn { "OpenTelemetry customizers are ignored under the Java agent: $customizers" }
    }
  }

  /** The resource that describes the application, as the SDK reports it. Empty when the Java agent owns the SDK. */
  val resource: Resource =
    if (agentAttached) Resource.empty()
    else customizers.fold(resource(config)) { resource, customizer -> customizer.customizeResource(resource) }

  private val sdk: OpenTelemetrySdk? =
    if (agentAttached) null
    else buildSdk(config, customizers, resource).also { if (config.registerGlobal) registerGlobal(it) }

  /** The application's OpenTelemetry: get tracers and meters from it, and hand it to instrumentation. */
  val openTelemetry: OpenTelemetry = sdk ?: GlobalOpenTelemetry.get()

  private var runtimeTelemetry: RuntimeTelemetry? = null

  override fun start() {
    if (sdk != null && config.metrics.enabled && config.metrics.runtime) {
      runtimeTelemetry = RuntimeTelemetry.create(openTelemetry)
    }
  }

  override fun shutdown() {
    runtimeTelemetry?.close()
    runtimeTelemetry = null
    sdk?.shutdown()?.join(config.shutdownTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
  }

  override fun ordering() = Ordering(DefaultPhase.Telemetry)

  companion object {
    private val log = logger()

    private fun isJavaAgentAttached(): Boolean = runCatching {
      Class.forName("io.opentelemetry.javaagent.OpenTelemetryAgent", false, ClassLoader.getSystemClassLoader())
    }.isSuccess

    private fun registerGlobal(sdk: OpenTelemetrySdk) {
      try {
        GlobalOpenTelemetry.set(sdk)
      } catch (e: IllegalStateException) {
        // e.g. a second SDK in the same process, as in a test; the first registered stays the global one
        log.warn { "Not registering the OpenTelemetry SDK globally, since another is already registered: ${e.message}" }
      }
    }
  }
}

private val SERVICE_NAME = AttributeKey.stringKey("service.name")
private val SERVICE_VERSION = AttributeKey.stringKey("service.version")
private val SERVICE_INSTANCE_ID = AttributeKey.stringKey("service.instance.id")
private val DEPLOYMENT_ENVIRONMENT_NAME = AttributeKey.stringKey("deployment.environment.name")

/**
 * The application's resource: the SDK's default, then [TelemetryConfig.resourceAttributes], then the typed attributes;
 * each wins over the ones before it. Every instance gets its own random `service.instance.id`, unless one is
 * configured, so backends can tell instances apart.
 */
internal fun resource(config: TelemetryConfig): Resource {
  val attributes = Attributes.builder()
  config.resourceAttributes.forEach { (key, value) -> attributes.put(key, value) }
  config.serviceName?.let { attributes.put(SERVICE_NAME, it) }
  config.serviceVersion?.let { attributes.put(SERVICE_VERSION, it) }
  config.serviceInstanceId?.let { attributes.put(SERVICE_INSTANCE_ID, it) }
  config.environment?.let { attributes.put(DEPLOYMENT_ENVIRONMENT_NAME, it) }

  val resource = Resource.getDefault().merge(Resource.create(attributes.build()))
  return if (resource.getAttribute(SERVICE_INSTANCE_ID) != null) resource
  else resource.merge(Resource.create(Attributes.of(SERVICE_INSTANCE_ID, UUID.randomUUID().toString())))
}

private fun buildSdk(
  config: TelemetryConfig,
  customizers: Collection<OpenTelemetryCustomizer>,
  resource: Resource,
): OpenTelemetrySdk {
  val endpoint = config.otlp.endpoint?.trimEnd('/')
  val headers = config.otlp.headers
  val builder = OpenTelemetrySdk.builder().setPropagators(
    ContextPropagators.create(
      TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance())
    )
  )

  if (config.traces.enabled) {
    val sampler = customizers.fold(Sampler.parentBased(Sampler.traceIdRatioBased(config.traces.sampleRatio))) { sampler, customizer ->
      customizer.customizeSampler(sampler)
    }
    val tracerProvider = SdkTracerProvider.builder().setResource(resource).setSampler(sampler)
    if (endpoint != null && config.traces.otlp) {
      val url = "$endpoint/v1/traces"
      val exporter = OtlpHttpSpanExporter.builder().setEndpoint(url).apply {
        headers.forEach { (key, value) -> addHeader(key, value.value) }
        if (config.otlp.compression) setCompression("gzip")
        config.otlp.timeout?.let { setTimeout(it.toJavaDuration()) }
        exportHeaders(customizers, url)?.let { setHeaders(it) }
        customizers.forEach { it.customizeSpanExporter(this, url) }
      }.build()
      tracerProvider.addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
    }
    customizers.forEach { it.customizeTracerProvider(tracerProvider) }
    builder.setTracerProvider(tracerProvider.build())
  } else {
    // the SDK's default tracer provider would record every span, for nothing
    builder.setTracerProvider(SdkTracerProvider.builder().setResource(resource).setSampler(Sampler.alwaysOff()).build())
  }

  if (config.metrics.enabled) {
    val meterProvider = SdkMeterProvider.builder().setResource(resource)
    if (endpoint != null && config.metrics.otlp) {
      val url = "$endpoint/v1/metrics"
      val exporter = OtlpHttpMetricExporter.builder().setEndpoint(url).apply {
        headers.forEach { (key, value) -> addHeader(key, value.value) }
        if (config.otlp.compression) setCompression("gzip")
        config.otlp.timeout?.let { setTimeout(it.toJavaDuration()) }
        exportHeaders(customizers, url)?.let { setHeaders(it) }
        customizers.forEach { it.customizeMetricExporter(this, url) }
      }.build()
      meterProvider.registerMetricReader(
        PeriodicMetricReader.builder(exporter).setInterval(config.metrics.interval.toJavaDuration()).build()
      )
    }
    customizers.forEach { it.customizeMeterProvider(meterProvider) }
    builder.setMeterProvider(meterProvider.build())
  }

  return builder.build()
}

/** Every customizer's [OpenTelemetryCustomizer.exportHeaders] for [endpoint], as the exporter's one header supplier. */
private fun exportHeaders(
  customizers: Collection<OpenTelemetryCustomizer>,
  endpoint: String,
): (() -> Map<String, String>)? {
  val suppliers = customizers.mapNotNull { it.exportHeaders(endpoint) }
  if (suppliers.isEmpty()) return null
  return { suppliers.fold(emptyMap()) { headers, supplier -> headers + supplier() } }
}
