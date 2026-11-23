@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.koin.bootableModule as koinBootableModule
import com.github.rocketraman.bootable.boot.setOfAppServices
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.boot.startPlan
import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule as koinHopliteConfigModule
import com.github.rocketraman.bootable.server.http.ktor.koin.ktorTelemetryModule as koinKtorTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.MetricsConfig
import com.github.rocketraman.bootable.telemetry.otel.MetricsSnapshot
import com.github.rocketraman.bootable.telemetry.otel.MetricsSnapshotProvider
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizers
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.PrometheusTelemetry
import com.github.rocketraman.bootable.telemetry.otel.PrometheusTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.TelemetryConfig
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule as koinOpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.koin.prometheusTelemetryModule as koinPrometheusTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.prometheusTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.withSpan
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.concurrent.CopyOnWriteArrayList
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.inBindSet
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.instance
import org.kodein.di.singleton
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

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

  fun serverSpans(): List<SpanData> = spans.filter { it.kind == SpanKind.SERVER }
}

/** An application server, as an application would write one, with its telemetry installed. */
private class AppServer(private val telemetry: KtorTelemetry) : CioKtorService("app", HostPort("127.0.0.1", 0)) {
  override fun Application.module() {
    installTelemetry(telemetry)
    routing {
      get("/hello") { call.respondText("hello") }
      healthRoutes(HealthLifecycleController())
      get("/orders/{id}") {
        val tracer = telemetry.openTelemetry.getTracer("orders")
        val order = tracer.withSpan("orders.find") { call.parameters["id"] }
        call.respondText("order $order")
      }
    }
  }
}

/** The application's telemetry, recorded in memory and exposed for Prometheus, as `OpenTelemetryService` would own it. */
private class Telemetry(config: KtorTelemetryConfig = KtorTelemetryConfig(), vararg customizers: OpenTelemetryCustomizer) {
  val inMemory = InMemoryTelemetry()
  val prometheus = PrometheusTelemetry()
  val service = OpenTelemetryService(
    TelemetryConfig(serviceName = "app", metrics = MetricsConfig(runtime = false), registerGlobal = false),
    listOf(inMemory, prometheus, *customizers),
  )
  val ktor = KtorTelemetry(service.openTelemetry, setOf(prometheus), config)
}

/** Starts [services] as Bootable does, in its start plan's order, runs [block], and stops them in reverse. */
private suspend fun withStarted(vararg services: AppService, block: suspend () -> Unit) {
  val ordered = startPlan(services.toList())
  ordered.forEach { it.start(CoroutineScope(SupervisorJob())) }
  try {
    block()
  } finally {
    ordered.reversed().forEach { it.shutdown() }
  }
}

private suspend fun AppService.port() = (this as KtorService<*, *>).server.engine.resolvedConnectors().single().port

private val client = HttpClient.newHttpClient()

private fun get(port: Int, path: String, vararg headers: Pair<String, String>): HttpResponse<String> {
  val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
    .apply { headers.forEach { (name, value) -> header(name, value) } }
    .build()
  return client.send(request, HttpResponse.BodyHandlers.ofString())
}

/**
 * A request's span and metrics are recorded as its response is sent, which can be just after the client has it. Waits
 * in real time: the tests run on virtual time, in which a `delay` takes none.
 */
private suspend fun eventually(condition: suspend () -> Boolean): Boolean {
  repeat(100) {
    if (condition()) return true
    Thread.sleep(20)
  }
  return condition()
}

private val HTTP_ROUTE = AttributeKey.stringKey("http.route")
private val URL_PATH = AttributeKey.stringKey("url.path")

val KtorTelemetryTest by testSuite {
  testSuite("requests") {
    test("are traced, with their route and status") {
      val telemetry = Telemetry()
      val server = AppServer(telemetry.ktor)
      withStarted(telemetry.service, server) {
        assert(get(server.port(), "/hello").body() == "hello")
        assert(eventually { telemetry.inMemory.serverSpans().isNotEmpty() })
      }
      val span = telemetry.inMemory.serverSpans().single()
      assert(span.name == "GET /hello")
      assert(span.attributes.get(HTTP_ROUTE) == "/hello")
      assert(span.attributes.get(AttributeKey.longKey("http.response.status_code")) == 200L)
      assert(span.resource.getAttribute(AttributeKey.stringKey("service.name")) == "app")
    }

    test("continue the caller's trace") {
      val telemetry = Telemetry()
      val server = AppServer(telemetry.ktor)
      val traceId = "4bf92f3577b34da6a3ce929d0e0e4736"
      withStarted(telemetry.service, server) {
        get(server.port(), "/hello", "traceparent" to "00-$traceId-00f067aa0ba902b7-01")
        assert(eventually { telemetry.inMemory.serverSpans().isNotEmpty() })
      }
      val span = telemetry.inMemory.serverSpans().single()
      assert(span.traceId == traceId)
      assert(span.parentSpanId == "00f067aa0ba902b7")
    }

    test("are the parent of the spans their routes start") {
      val telemetry = Telemetry()
      val server = AppServer(telemetry.ktor)
      withStarted(telemetry.service, server) {
        assert(get(server.port(), "/orders/42").body() == "order 42")
        assert(eventually { telemetry.inMemory.serverSpans().isNotEmpty() })
      }
      val request = telemetry.inMemory.serverSpans().single()
      val find = telemetry.inMemory.spans.single { it.name == "orders.find" }
      assert(find.parentSpanId == request.spanId)
      assert(request.attributes.get(HTTP_ROUTE) == "/orders/{id}")
    }

    test("are measured as http.server.request.duration") {
      val telemetry = Telemetry()
      val server = AppServer(telemetry.ktor)
      withStarted(telemetry.service, server) {
        get(server.port(), "/hello")
        assert(eventually {
          telemetry.inMemory.metrics.collectAllMetrics().any { it.name == "http.server.request.duration" }
        })
      }
    }

    test("record the configured headers on their spans") {
      val telemetry = Telemetry(KtorTelemetryConfig(capturedRequestHeaders = listOf("X-Request-Id")))
      val server = AppServer(telemetry.ktor)
      withStarted(telemetry.service, server) {
        get(server.port(), "/hello", "X-Request-Id" to "req-1")
        assert(eventually { telemetry.inMemory.serverSpans().isNotEmpty() })
      }
      val header = telemetry.inMemory.serverSpans().single().attributes
        .get(AttributeKey.stringArrayKey("http.request.header.x-request-id"))
      assert(header == listOf("req-1"))
    }

    test("go through the customizer") {
      val inMemory = InMemoryTelemetry()
      val service = OpenTelemetryService(TelemetryConfig(metrics = MetricsConfig(runtime = false), registerGlobal = false), listOf(inMemory))
      val ktor = KtorTelemetry(service.openTelemetry) { builder ->
        builder.attributesExtractor { onStart { attributes.put("customized", "yes") } }
      }
      val server = AppServer(ktor)
      withStarted(service, server) {
        get(server.port(), "/hello")
        assert(eventually { inMemory.serverSpans().isNotEmpty() })
      }
      assert(inMemory.serverSpans().single().attributes.get(AttributeKey.stringKey("customized")) == "yes")
    }
  }

  testSuite("the scrape, on the application's server") {
    test("serves the Prometheus metrics, including the request metrics") {
      val telemetry = Telemetry()
      val server = AppServer(telemetry.ktor)
      withStarted(telemetry.service, server) {
        val port = server.port()
        get(port, "/hello")
        lateinit var scrape: HttpResponse<String>
        assert(eventually {
          scrape = get(port, "/metrics")
          scrape.body().lines().any { it.startsWith("http_server_request_duration_seconds_count{") && """http_route="/hello"""" in it }
        })
        assert(scrape.statusCode() == 200)
        assert(scrape.headers().firstValue("Content-Type").get().startsWith("text/plain; version=0.0.4"))
      }
    }

    test("answers in OpenMetrics when the scraper asks for it") {
      val telemetry = Telemetry()
      val server = AppServer(telemetry.ktor)
      withStarted(telemetry.service, server) {
        val scrape = get(server.port(), "/metrics", "Accept" to "application/openmetrics-text;version=1.0.0,text/plain;q=0.5")
        assert(scrape.headers().firstValue("Content-Type").get().startsWith("application/openmetrics-text"))
        assert(scrape.body().trimEnd().endsWith("# EOF"))
      }
    }

    test("is at the configured path") {
      val telemetry = Telemetry(KtorTelemetryConfig(path = "/internal/metrics"))
      val server = AppServer(telemetry.ktor)
      withStarted(telemetry.service, server) {
        assert(get(server.port(), "/internal/metrics").statusCode() == 200)
        assert(get(server.port(), "/metrics").statusCode() == 404)
      }
    }

    test("is not traced, with excludeScrapeFromTraces") {
      val config = KtorTelemetryConfig()
      val telemetry = Telemetry(config, config.excludeScrapeFromTraces())
      val server = AppServer(telemetry.ktor)
      withStarted(telemetry.service, server) {
        get(server.port(), "/metrics")
        get(server.port(), "/hello")
        assert(eventually { telemetry.inMemory.serverSpans().isNotEmpty() })
        // the scrape came first, so its span, had it been sampled, would be in by now
      }
      assert(telemetry.inMemory.serverSpans().map { it.attributes.get(URL_PATH) } == listOf("/hello"))
    }

    test("health probes are not traced, with excludeHealthFromTraces") {
      val health = KtorHealthConfig()
      val telemetry = Telemetry(KtorTelemetryConfig(), health.excludeHealthFromTraces())
      val server = AppServer(telemetry.ktor)
      withStarted(telemetry.service, server) {
        get(server.port(), "/health/live")
        get(server.port(), "/health/ready")
        get(server.port(), "/hello")
        assert(eventually { telemetry.inMemory.serverSpans().isNotEmpty() })
      }
      assert(telemetry.inMemory.serverSpans().map { it.attributes.get(URL_PATH) } == listOf("/hello"))
    }

    test("isn't served without a pulled backend") {
      val telemetry = Telemetry()
      val server = AppServer(KtorTelemetry(telemetry.service.openTelemetry))
      withStarted(telemetry.service, server) {
        assert(get(server.port(), "/hello").statusCode() == 200)
        assert(get(server.port(), "/metrics").statusCode() == 404)
      }
    }
  }

  testSuite("the scrape, on a listener of its own") {
    test("is served there, and not on the application's server") {
      val telemetry = Telemetry(KtorTelemetryConfig(hostPort = "127.0.0.1:0"))
      val server = AppServer(telemetry.ktor)
      val listener = telemetry.ktor.listener(CIO)
      assert(listener is MetricsKtorService<*, *>)
      withStarted(telemetry.service, server, listener) {
        assert(get(server.port(), "/hello").statusCode() == 200)
        assert(get(server.port(), "/metrics").statusCode() == 404)
        assert(eventually {
          get(listener.port(), "/metrics").body().lines().any { it.startsWith("http_server_request_duration_seconds_count{") }
        })
        assert(get(listener.port(), "/hello").statusCode() == 404)
      }
      // the listener isn't traced: the only /metrics span is the application server's 404
      val metricsSpans = telemetry.inMemory.serverSpans().filter { it.attributes.get(URL_PATH) == "/metrics" }
      assert(metricsSpans.map { it.attributes.get(AttributeKey.longKey("http.response.status_code")) } == listOf(404L))
    }

    test("stops before the SDK shuts down, so a scrape at shutdown is answered") {
      val telemetry = Telemetry(KtorTelemetryConfig(hostPort = "127.0.0.1:0"))
      val listener = telemetry.ktor.listener(CIO)
      assert(startPlan(listOf(listener, telemetry.service)) == listOf(telemetry.service, listener))
    }

    test("is a service that does nothing when no hostPort is configured") {
      val listener = Telemetry().ktor.listener(CIO)
      assert(listener !is KtorService<*, *>)
      listener.start()
      listener.shutdown()
    }

    test("is on the application's KtorServerEngine, when it has one") {
      val telemetry = Telemetry(KtorTelemetryConfig(hostPort = "127.0.0.1:0"))
      assert(telemetry.ktor.listener(KtorServerEngine(CIO)) is MetricsKtorService<*, *>)
    }

    test("needs no KtorServerEngine when no hostPort is configured") {
      assert(Telemetry().ktor.listener(engine = null) !is KtorService<*, *>)
    }

    test("fails with a hostPort but no KtorServerEngine") {
      val telemetry = Telemetry(KtorTelemetryConfig(hostPort = ":0"))
      val failure = runCatching { telemetry.ktor.listener(engine = null) }.exceptionOrNull()
      assert(failure?.message?.contains("no KtorServerEngine") == true)
    }

    test("fails with a hostPort but no pulled backend to serve") {
      val telemetry = Telemetry()
      val ktor = KtorTelemetry(telemetry.service.openTelemetry, config = KtorTelemetryConfig(hostPort = ":0"))
      assert(runCatching { ktor.listener(CIO) }.exceptionOrNull()?.message?.contains("no metrics scrape backend") == true)
    }
  }

  test("only one scrape backend can be served") {
    val provider = MetricsSnapshotProvider { MetricsSnapshot("text/plain", ByteArray(0)) }
    val other = MetricsSnapshotProvider { MetricsSnapshot("text/plain", ByteArray(0)) }
    val failure = runCatching { KtorTelemetry(Telemetry().service.openTelemetry, setOf(provider, other)) }.exceptionOrNull()
    assert(failure is IllegalArgumentException)
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
  val ktorTelemetry: KtorTelemetry
  val inMemoryTelemetry: InMemoryTelemetry
}

@BindingContainer
object TestTelemetryBindings {
  @Provides @SingleIn(TestScope::class) fun inMemoryTelemetry(): InMemoryTelemetry = InMemoryTelemetry()

  @Provides @IntoSet fun inMemory(telemetry: InMemoryTelemetry): OpenTelemetryCustomizer = telemetry
}

/** An application's module, composed as an Inara application composes it, with the SDK's telemetry kept in memory. */
@DIModule(composed = true)
interface InaraTestModule :
  ConfigModule,
  BootableModule,
  OpenTelemetryModule,
  PrometheusTelemetryModule,
  KtorServerEngineModule,
  KtorTelemetryModule {
  @OpenTelemetryCustomizers val inMemoryTelemetry: InMemoryTelemetry
}

private val expectedConfig = KtorTelemetryConfig(path = "/scrape", capturedRequestHeaders = listOf("X-Request-Id"))

/** Serves [ktorTelemetry] on a real server, as the wired application would, and checks the traces and the scrape. */
private suspend fun checkServes(services: Set<AppService>, ktorTelemetry: KtorTelemetry, inMemory: InMemoryTelemetry) {
  val server = AppServer(ktorTelemetry)
  withStarted(*services.toTypedArray(), server) {
    get(server.port(), "/health/live")
    get(server.port(), "/hello", "X-Request-Id" to "req-1")
    assert(eventually {
      get(server.port(), "/scrape").body().lines().any { it.startsWith("http_server_request_duration_seconds_count{") }
    })
  }
  // the scrapes and the health probes aren't traced, and the configured header is captured
  val span = inMemory.serverSpans().single()
  assert(span.attributes.get(URL_PATH) == "/hello")
  assert(span.attributes.get(AttributeKey.stringArrayKey("http.request.header.x-request-id")) == listOf("req-1"))
}

/** Config comes from src/test/resources/application.conf, as it would in an application. */
val KtorTelemetryWiringTest by testSuite {
  testSuite("ktor telemetry config decoded by") {
    test("hoplite") {
      assert(hopliteConfigBinder(report = false, strict = true).config<KtorTelemetryConfig>("telemetry.ktor") == expectedConfig)
    }

    test("kodein") {
      val inMemory = InMemoryTelemetry()
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(openTelemetryModule)
        import(prometheusTelemetryModule)
        import(ktorTelemetryModule)
        inBindSet<OpenTelemetryCustomizer> { add { singleton { inMemory } } }
      }.direct

      val ktorTelemetry = di.instance<KtorTelemetry>()
      assert(ktorTelemetry.config == expectedConfig)
      val services = di.instance<Set<AppService>>()
      assert(services.single { it.name().startsWith("MetricsKtorService") }.name() == "MetricsKtorService (none)")
      checkServes(services, ktorTelemetry, inMemory)
    }

    test("koin") {
      val inMemory = InMemoryTelemetry()
      val koin = koinApplication {
        modules(
          koinBootableModule(),
          koinHopliteConfigModule(),
          koinOpenTelemetryModule(),
          koinPrometheusTelemetryModule(),
          koinKtorTelemetryModule(),
          module { single<OpenTelemetryCustomizer>(named("test")) { inMemory } },
        )
      }.koin

      try {
        val ktorTelemetry = koin.get<KtorTelemetry>()
        assert(ktorTelemetry.config == expectedConfig)
        val services = koin.getAll<AppService>().toSet()
        assert(services.single { it.name().startsWith("MetricsKtorService") }.name() == "MetricsKtorService (none)")
        checkServes(services, ktorTelemetry, inMemory)
      } finally {
        koin.close()
      }
    }

    test("metro") {
      val graph = createGraph<TestGraph>()

      assert(graph.ktorTelemetry.config == expectedConfig)
      assert(graph.appServices.single { it.name().startsWith("MetricsKtorService") }.name() == "MetricsKtorService (none)")
      checkServes(graph.appServices, graph.ktorTelemetry, graph.inMemoryTelemetry)
    }

    test("inara") {
      val module = InaraTestModule(
        configModule = hopliteConfigModule(),
        bootableModule = bootableModule(),
        openTelemetryModule = openTelemetryModule(),
        prometheusTelemetryModule = prometheusTelemetryModule(),
        ktorServerEngineModule = NoKtorServerEngineModule,
        ktorTelemetryModule = ktorTelemetryModule(),
        inMemoryTelemetry = Binding.single { InMemoryTelemetry() },
      )

      assert(module.ktorTelemetry.config == expectedConfig)
      val services = module.setOfAppServices()
      assert(services.single { it.name().startsWith("MetricsKtorService") }.name() == "MetricsKtorService (none)")
      checkServes(services, module.ktorTelemetry, module.inMemoryTelemetry)
    }
  }
}
