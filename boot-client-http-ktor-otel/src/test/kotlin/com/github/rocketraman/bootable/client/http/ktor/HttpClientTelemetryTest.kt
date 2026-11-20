package com.github.rocketraman.bootable.client.http.ktor

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.setOfAppServices
import com.github.rocketraman.bootable.boot.startPlan
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.HopliteConfigModule
import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.server.http.ktor.CioKtorService
import com.github.rocketraman.bootable.server.http.ktor.KtorService
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.server.http.ktor.installTelemetry
import com.github.rocketraman.bootable.telemetry.otel.MetricsConfig
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizers
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.TelemetryConfig
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.withRootSpan
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.inBindSet
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.instance
import org.kodein.di.singleton
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Collects every span and metric the SDK records, in memory, as a test's window onto it. The spans outlive the SDK's
 * shutdown, unlike an `InMemorySpanExporter`'s.
 */
class InMemoryTelemetry : OpenTelemetryCustomizer {
  val spans = CopyOnWriteArrayList<SpanData>()
  val metrics: InMemoryMetricReader = InMemoryMetricReader.create()

  override fun customizeTracerProvider(builder: SdkTracerProviderBuilder) {
    builder.addSpanProcessor(SimpleSpanProcessor.create(object : SpanExporter {
      override fun export(spans: Collection<SpanData>): CompletableResultCode {
        this@InMemoryTelemetry.spans += spans
        return CompletableResultCode.ofSuccess()
      }
      override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()
      override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
    }))
  }

  override fun customizeMeterProvider(builder: SdkMeterProviderBuilder) {
    builder.registerMetricReader(metrics)
  }

  fun spans(kind: SpanKind): List<SpanData> = spans.filter { it.kind == kind }
}

/** A downstream server, as a MockEngine, that records each request's headers and answers with an `X-Request-Id`. */
class Downstream : KtorClientEngine {
  val requests = CopyOnWriteArrayList<HttpRequestData>()

  override fun create(client: KtorClientSpec) = MockEngine { request ->
    requests += request
    respond("downstream", headers = headersOf("X-Request-Id", "req-1"))
  }

  fun traceparent(): String = requests.single().headers["traceparent"]!!
}

/** The application's telemetry and clients, as `OpenTelemetryService` and `KtorClientService` would own them. */
private class Telemetry(config: HttpClientTelemetryConfig = HttpClientTelemetryConfig()) {
  val inMemory = InMemoryTelemetry()
  val service = OpenTelemetryService(
    TelemetryConfig(serviceName = "app", metrics = MetricsConfig(runtime = false), registerGlobal = false),
    listOf(inMemory),
  )
  val downstream = Downstream()
  val clients = KtorClientService(
    KtorClientsConfig(KtorClientConfig(baseUrl = "https://downstream.test/")),
    downstream,
    HttpClientTelemetry(service.openTelemetry, config),
  )
}

/** An application server whose route calls downstream through the client, with the server's telemetry installed. */
private class AppServer(private val telemetry: KtorTelemetry, private val clients: KtorClientService) :
  CioKtorService("app", HostPort("127.0.0.1", 0)) {
  override fun Application.module() {
    installTelemetry(telemetry)
    routing {
      get("/relay") { call.respondText(clients.default.get("things/1").bodyAsText()) }
    }
  }
}

/** Starts [services] as Bootable does, in its start plan's order, runs [block], and stops them in reverse. */
private suspend fun withStarted(vararg services: AppService, block: suspend () -> Unit) {
  val ordered = startPlan(services.toList())
  ordered.forEach { if (it is KtorService<*, *>) it.start {} else it.start() }
  try {
    block()
  } finally {
    ordered.reversed().forEach { it.shutdown() }
  }
}

/** Spans end just after the response is had; waits in real time, since the tests run on virtual time. */
private fun eventually(condition: () -> Boolean): Boolean {
  repeat(100) {
    if (condition()) return true
    Thread.sleep(20)
  }
  return condition()
}

private fun get(port: Int, path: String): String =
  URI("http://127.0.0.1:$port$path").toURL().readText()

private suspend fun KtorService<*, *>.port() = server.engine.resolvedConnectors().single().port

val HttpClientTelemetryTest by testSuite {
  testSuite("a client request") {
    test("is a client span, a child of the current span, and sends its trace in a traceparent header") {
      val telemetry = Telemetry()
      withStarted(telemetry.service, telemetry.clients) {
        telemetry.service.openTelemetry.getTracer("test").withRootSpan("job") {
          telemetry.clients.default.get("things/1")
        }
      }
      val job = telemetry.inMemory.spans.single { it.name == "job" }
      val client = telemetry.inMemory.spans(SpanKind.CLIENT).single()
      assert(client.name == "GET")
      assert(client.parentSpanId == job.spanId && client.traceId == job.traceId)
      assert(client.attributes.get(AttributeKey.stringKey("url.full")) == "https://downstream.test/things/1")
      assert(client.attributes.get(AttributeKey.longKey("http.response.status_code")) == 200L)
      assert(telemetry.downstream.traceparent().startsWith("00-${client.traceId}-${client.spanId}-"))
    }

    test("made within a server request is a child of the server's span") {
      val telemetry = Telemetry()
      val server = AppServer(KtorTelemetry(telemetry.service.openTelemetry), telemetry.clients)
      withStarted(telemetry.service, telemetry.clients, server) {
        assert(get(server.port(), "/relay") == "downstream")
        assert(eventually { telemetry.inMemory.spans(SpanKind.SERVER).isNotEmpty() })
      }
      val request = telemetry.inMemory.spans(SpanKind.SERVER).single()
      val client = telemetry.inMemory.spans(SpanKind.CLIENT).single()
      assert(client.parentSpanId == request.spanId && client.traceId == request.traceId)
      assert(telemetry.downstream.traceparent().contains(request.traceId))
    }

    test("is measured as http.client.request.duration") {
      val telemetry = Telemetry()
      withStarted(telemetry.service, telemetry.clients) {
        telemetry.clients.default.get("things/1")
        assert(telemetry.inMemory.metrics.collectAllMetrics().any { it.name == "http.client.request.duration" })
      }
    }

    test("records the configured headers on its span") {
      val telemetry = Telemetry(HttpClientTelemetryConfig(capturedResponseHeaders = listOf("X-Request-Id")))
      withStarted(telemetry.service, telemetry.clients) {
        telemetry.clients.default.get("things/1")
      }
      val header = telemetry.inMemory.spans(SpanKind.CLIENT).single().attributes
        .get(AttributeKey.stringArrayKey("http.response.header.x-request-id"))
      assert(header == listOf("req-1"))
    }

    test("goes through the customizer") {
      val inMemory = InMemoryTelemetry()
      val service = OpenTelemetryService(TelemetryConfig(metrics = MetricsConfig(runtime = false), registerGlobal = false), listOf(inMemory))
      val telemetry = HttpClientTelemetry(service.openTelemetry) { builder ->
        builder.attributesExtractor { onStart { attributes.put("customized", "yes") } }
      }
      val clients = KtorClientService(KtorClientsConfig(), Downstream(), telemetry)
      withStarted(service, clients) {
        clients.default.get("https://downstream.test/")
      }
      assert(inMemory.spans(SpanKind.CLIENT).single().attributes.get(AttributeKey.stringKey("customized")) == "yes")
    }
  }

  test("the clients stop before the SDK shuts down, so their last spans are exported") {
    val telemetry = Telemetry()
    assert(startPlan(listOf(telemetry.clients, telemetry.service)) == listOf(telemetry.service, telemetry.clients))
    telemetry.clients.shutdown()
  }
}

sealed interface TestScope

@DependencyGraph(
  TestScope::class,
  additionalScopes = [BootScope::class],
  // named rather than contributed: a @ContributesTo in test code trips the power-assert bug KT-83931 on the hint
  // file Metro generates for it
  bindingContainers = [TestTelemetryBindings::class],
)
interface TestGraph : BootableGraph {
  val clients: KtorClientService
  val httpClientTelemetry: HttpClientTelemetry
  val inMemoryTelemetry: InMemoryTelemetry
  val downstream: Downstream
}

@BindingContainer
object TestTelemetryBindings {
  @Provides @SingleIn(TestScope::class) fun inMemoryTelemetry(): InMemoryTelemetry = InMemoryTelemetry()

  @Provides @IntoSet fun inMemory(telemetry: InMemoryTelemetry): OpenTelemetryCustomizer = telemetry

  @Provides @SingleIn(TestScope::class) fun downstream(): Downstream = Downstream()

  @Provides fun engine(downstream: Downstream): KtorClientEngine = downstream
}

/** An application's module, composed as an Inara application composes it, with the test's in-memory telemetry. */
@DIModule(composed = true)
interface InaraTestModule :
  ConfigModule,
  BootableModule,
  OpenTelemetryModule,
  KtorClientModule,
  HttpClientTelemetryModule {
  @OpenTelemetryCustomizers val inMemoryTelemetry: OpenTelemetryCustomizer
}

private val expectedConfig = HttpClientTelemetryConfig(capturedResponseHeaders = listOf("X-Request-Id"))

/** Calls downstream through the wired client, as the application would, and checks its span. */
private suspend fun checkTraces(services: Set<AppService>, clients: KtorClientService, inMemory: InMemoryTelemetry) {
  withStarted(*services.toTypedArray()) {
    clients.default.get("things/1")
  }
  val span = inMemory.spans(SpanKind.CLIENT).single()
  assert(span.attributes.get(AttributeKey.stringKey("url.full")) == "https://downstream.test/things/1")
  assert(span.attributes.get(AttributeKey.stringArrayKey("http.response.header.x-request-id")) == listOf("req-1"))
}

/** Config comes from src/test/resources/application.conf, as it would in an application. */
val HttpClientTelemetryWiringTest by testSuite {
  testSuite("client telemetry config decoded by") {
    test("hoplite") {
      val config = hopliteConfigBinder(report = false, strict = true).config<HttpClientTelemetryConfig>("telemetry.httpClient")
      assert(config == expectedConfig)
    }

    test("kodein") {
      val inMemory = InMemoryTelemetry()
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(openTelemetryModule)
        import(ktorClientModule)
        import(httpClientTelemetryModule)
        bindSingleton<KtorClientEngine> { Downstream() }
        inBindSet<OpenTelemetryCustomizer> { add { singleton { inMemory } } }
      }.direct

      assert(di.instance<HttpClientTelemetry>().config == expectedConfig)
      checkTraces(di.instance(), di.instance(), inMemory)
    }

    test("metro") {
      val graph = createGraph<TestGraph>()

      assert(graph.httpClientTelemetry.config == expectedConfig)
      checkTraces(graph.appServices, graph.clients, graph.inMemoryTelemetry)
    }

    test("inara") {
      val inMemory = InMemoryTelemetry()
      val module = InaraTestModule(
        configModule = HopliteConfigModule(),
        bootableModule = bootableModule(),
        openTelemetryModule = openTelemetryModule(),
        ktorClientModule = ktorClientModule(Downstream()),
        httpClientTelemetryModule = httpClientTelemetryModule(),
        inMemoryTelemetry = Binding.value(inMemory),
      )

      assert(module.httpClientTelemetry.config == expectedConfig)
      checkTraces(module.setOfAppServices(), module.ktorClientService, inMemory)
    }
  }
}
