package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.telemetry.otel.MetricsSnapshotProvider
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.excludeUrlPathsFromTraces
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetryBuilder

/**
 * How a Ktor server reports its telemetry. Every field is optional.
 *
 * Decoded from the `telemetry.ktor` config section:
 *
 * ```
 * telemetry {
 *   ktor {
 *     # serve the metrics scrape on its own port, rather than on the application's server
 *     hostPort = ":9090"
 *     capturedRequestHeaders = [X-Request-Id]
 *   }
 * }
 * ```
 */
data class KtorTelemetryConfig(
  /** The path of the metrics scrape route. */
  val path: String = DEFAULT_PATH,
  /**
   * Where to serve the metrics scrape on a listener of its own, such as `:9090`. Unset, the scrape is served on the
   * application's server instead. A listener of its own keeps the metrics off a server that is public, without any
   * authentication, since only what can reach that port can scrape.
   */
  val hostPort: String? = null,
  /** Request headers to record on each request's span, as `http.request.header.<name>`. */
  val capturedRequestHeaders: List<String> = emptyList(),
  /** Response headers to record on each request's span, as `http.response.header.<name>`. */
  val capturedResponseHeaders: List<String> = emptyList(),
) {
  /**
   * An `OpenTelemetryCustomizer` that records no trace for a scrape served on the application's server, which
   * Prometheus would otherwise fill the traces with. Add it to `OpenTelemetryService`'s customizers.
   */
  fun excludeScrapeFromTraces(): OpenTelemetryCustomizer =
    excludeUrlPathsFromTraces(if (hostPort == null) setOf(path) else emptySet())

  companion object {
    const val DEFAULT_PATH = "/metrics"
  }
}

/**
 * Adjusts OpenTelemetry's Ktor server instrumentation after the typed settings are applied, for settings
 * [KtorTelemetryConfig] doesn't cover, such as extra span attributes or how a span's status is decided.
 */
fun interface KtorServerTelemetryCustomizer {
  fun customize(builder: KtorServerTelemetryBuilder)
}

/**
 * A Ktor server's telemetry: OpenTelemetry's Ktor server instrumentation, which traces every request (continuing the
 * caller's trace when the request carries one) and records the standard HTTP server metrics, such as
 * `http.server.request.duration`, and the metrics scrape route for a pulled backend, such as Prometheus, served by
 * [snapshotProviders].
 *
 * Install it in your server's module with [installTelemetry]:
 *
 * ```
 * class MyServer(config: ServerConfig, private val telemetry: KtorTelemetry) : CioKtorService("my-server", config) {
 *   override fun Application.module() {
 *     installTelemetry(telemetry)
 *     routing { ... }
 *   }
 * }
 * ```
 *
 * The request's span is current while its route runs, so spans started there, e.g. with `Tracer.withSpan`, are its
 * children, and log events carry its trace id.
 *
 * The scrape is served on that server, unless [KtorTelemetryConfig.hostPort] puts it on a listener of its own: then
 * add [listener] to the application's services too.
 */
class KtorTelemetry(
  val openTelemetry: OpenTelemetry,
  snapshotProviders: Set<MetricsSnapshotProvider> = emptySet(),
  val config: KtorTelemetryConfig = KtorTelemetryConfig(),
  private val customizer: KtorServerTelemetryCustomizer = KtorServerTelemetryCustomizer { },
) {
  init {
    require(snapshotProviders.size <= 1) {
      "Only one scrape route can be served, but there are ${snapshotProviders.size} metrics snapshot providers"
    }
  }

  /** The pulled metrics backend to serve, if there is one. */
  val snapshotProvider: MetricsSnapshotProvider? = snapshotProviders.singleOrNull()

  /**
   * Installs OpenTelemetry's Ktor server instrumentation, and the scrape route, unless it has a listener of its own or
   * there is no pulled backend.
   */
  fun install(application: Application) {
    application.install(KtorServerTelemetry) {
      setOpenTelemetry(this@KtorTelemetry.openTelemetry)
      capturedRequestHeaders(this@KtorTelemetry.config.capturedRequestHeaders)
      capturedResponseHeaders(this@KtorTelemetry.config.capturedResponseHeaders)
      customizer.customize(this)
    }
    if (config.hostPort == null && snapshotProvider != null) {
      // this@KtorTelemetry: inside routing, a bare `path` would be the routing node's own
      application.routing { metricsRoute(snapshotProvider, this@KtorTelemetry.config.path) }
    }
  }

  /**
   * The scrape's listener of its own, on the [engineFactory] engine, when [KtorTelemetryConfig.hostPort] is set;
   * otherwise a service that does nothing, so the listener can be added to the application's services either way.
   */
  fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> listener(
    engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
  ): AppService {
    val hostPort = config.hostPort ?: return NoMetricsListener
    val snapshotProvider = checkNotNull(snapshotProvider) {
      "telemetry.ktor.hostPort is $hostPort, but there is no metrics scrape backend, such as Prometheus, to serve there"
    }
    return MetricsKtorService(HostPort(hostPort), engineFactory, snapshotProvider, config.path)
  }

  private object NoMetricsListener : AppService {
    override fun name() = "MetricsKtorService (none)"
  }
}

/** Installs [telemetry] in this application: see [KtorTelemetry.install]. */
fun Application.installTelemetry(telemetry: KtorTelemetry) = telemetry.install(this)

/** Serves [snapshotProvider]'s metrics at [path], for scraping, in the format the request's `Accept` header prefers. */
fun Route.metricsRoute(snapshotProvider: MetricsSnapshotProvider, path: String = KtorTelemetryConfig.DEFAULT_PATH) {
  get(path) {
    val scrape = snapshotProvider.snapshot(call.request.header(HttpHeaders.Accept))
    call.respondBytes(scrape.body, ContentType.parse(scrape.contentType))
  }
}

/**
 * A listener that serves only [snapshotProvider]'s metrics, at [path], on a port of its own: e.g. a scrape port
 * reachable only from inside the cluster, next to a public application server. It isn't traced. Like any
 * [KtorService], it starts last and stops first, so a scrape in flight at shutdown is still answered.
 */
class MetricsKtorService<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
  hostPort: HostPort,
  engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
  private val snapshotProvider: MetricsSnapshotProvider,
  private val path: String = KtorTelemetryConfig.DEFAULT_PATH,
  name: String = "metrics",
) : KtorService<TEngine, TConfiguration>(name, hostPort, engineFactory) {
  override fun Application.module() {
    // this@MetricsKtorService: inside routing, a bare `path` would be the routing node's own
    routing { metricsRoute(snapshotProvider, this@MetricsKtorService.path) }
  }
}
