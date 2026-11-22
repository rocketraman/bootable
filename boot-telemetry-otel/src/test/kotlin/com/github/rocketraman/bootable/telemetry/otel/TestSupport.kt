package com.github.rocketraman.bootable.telemetry.otel

import com.sun.net.httpserver.HttpServer
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/** Collects every span and metric the SDK records, in memory, as a test's window onto it. */
class InMemoryTelemetry : OpenTelemetryCustomizer {
  val spans: InMemorySpanExporter = InMemorySpanExporter.create()
  val metrics: InMemoryMetricReader = InMemoryMetricReader.create()

  override fun customizeTracerProvider(builder: SdkTracerProviderBuilder) {
    builder.addSpanProcessor(SimpleSpanProcessor.create(spans))
  }

  override fun customizeMeterProvider(builder: SdkMeterProviderBuilder) {
    builder.registerMetricReader(metrics)
  }

  fun metricNames(): Set<String> = metrics.collectAllMetrics().map { it.name }.toSet()
}

internal class ExportRequest(val path: String, val headers: Map<String, String>, val body: String)

/** An OTLP receiver that only records what is exported to it. */
internal suspend fun withCollector(block: suspend (endpoint: String, requests: List<ExportRequest>) -> Unit) {
  val requests = CopyOnWriteArrayList<ExportRequest>()
  val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  server.createContext("/") { exchange ->
    // protobuf encodes strings as plain UTF-8, so names and attributes can be found in the body as text
    val body = exchange.requestBody.readBytes().decodeToString()
    requests += ExportRequest(exchange.requestURI.path, exchange.requestHeaders.mapValues { it.value.joinToString() }, body)
    exchange.sendResponseHeaders(200, -1)
    exchange.close()
  }
  server.start()
  try {
    block("http://127.0.0.1:${server.address.port}", requests)
  } finally {
    server.stop(0)
  }
}

/** A config for tests: nothing global, no runtime metrics unless asked for. */
internal fun testConfig(
  otlp: OtlpConfig = OtlpConfig(),
  traces: TracesConfig = TracesConfig(),
  metrics: MetricsConfig = MetricsConfig(runtime = false),
  serviceName: String? = "test-service",
) = TelemetryConfig(serviceName = serviceName, otlp = otlp, traces = traces, metrics = metrics, registerGlobal = false)
