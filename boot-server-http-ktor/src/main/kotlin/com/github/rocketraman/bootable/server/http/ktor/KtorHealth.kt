package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Health
import com.github.rocketraman.bootable.boot.HealthReport
import com.github.rocketraman.bootable.boot.HealthStatus
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.config.config
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where a Ktor server serves the application's [Health]. Every field is optional.
 *
 * Decoded from the `health.ktor` config section:
 *
 * ```
 * health {
 *   ktor {
 *     # serve the probes on their own port, rather than on the application's server
 *     hostPort = ":9091"
 *   }
 * }
 * ```
 */
data class KtorHealthConfig(
  /** The path of the liveness route. */
  val livePath: String = DEFAULT_LIVE_PATH,
  /** The path of the readiness route. */
  val readyPath: String = DEFAULT_READY_PATH,
  /**
   * Where to serve the routes on a listener of their own, such as `:9091`. Unset, they are served on the application's
   * server instead. A listener of their own starts first and stops last, so liveness is answered while the
   * application starts, such as during long migrations, and readiness is down until it is running, and from when it
   * starts to shut down until it has stopped.
   */
  val hostPort: String? = null,
) {
  /** The paths served on the application's server: none when the routes have a listener of their own. */
  val applicationServerPaths: Set<String> get() = if (hostPort == null) setOf(livePath, readyPath) else emptySet()

  companion object {
    const val DEFAULT_LIVE_PATH = "/health/live"
    const val DEFAULT_READY_PATH = "/health/ready"
  }
}

/** Decodes [KtorHealthConfig] from the `health.ktor` config section using the given [ConfigBinder]. */
fun ktorHealthConfig(binder: ConfigBinder): KtorHealthConfig = binder.config("health.ktor")

/**
 * Serves the application's [Health] from a Ktor server: liveness at [KtorHealthConfig.livePath] and readiness at
 * [KtorHealthConfig.readyPath], each `200 OK` when healthy and `503 Service Unavailable` when not, with a short
 * plain-text body: `UP` or `DOWN`, the lifecycle state, and each readiness check's result.
 *
 * Install it in your server's module with [installHealth]:
 *
 * ```
 * class MyServer(config: ServerConfig, private val health: KtorHealth) : CioKtorService("my-server", config) {
 *   override fun Application.module() {
 *     installHealth(health)
 *     routing { ... }
 *   }
 * }
 * ```
 *
 * The routes are served on that server, unless [KtorHealthConfig.hostPort] puts them on a listener of their own:
 * then add [listener] to the application's services too.
 */
class KtorHealth(
  val health: Health,
  val config: KtorHealthConfig = KtorHealthConfig(),
) {
  /** Installs the routes, unless they have a listener of their own. */
  fun install(application: Application) {
    if (config.hostPort == null) {
      application.routing { healthRoutes(health, this@KtorHealth.config.livePath, this@KtorHealth.config.readyPath) }
    }
  }

  /**
   * The routes' listener of their own, on the [engineFactory] engine, when [KtorHealthConfig.hostPort] is set;
   * otherwise a service that does nothing, so the listener can be added to the application's services either way.
   */
  fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> listener(
    engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
  ): AppService {
    val hostPort = config.hostPort ?: return NoHealthListener
    return HealthKtorService(HostPort(hostPort), engineFactory, health, config.livePath, config.readyPath)
  }

  private object NoHealthListener : AppService {
    override fun name() = "HealthKtorService (none)"
  }
}

/** Installs [health]'s routes in this application: see [KtorHealth.install]. */
fun Application.installHealth(health: KtorHealth) = health.install(this)

/**
 * Serves [health]'s liveness at [livePath] and readiness at [readyPath]: `200 OK` when healthy, `503 Service
 * Unavailable` when not, with a short plain-text body, which no cache keeps.
 */
fun Route.healthRoutes(
  health: Health,
  livePath: String = KtorHealthConfig.DEFAULT_LIVE_PATH,
  readyPath: String = KtorHealthConfig.DEFAULT_READY_PATH,
) {
  get(livePath) { call.respondHealth(health.liveness()) }
  // the checks may block, such as on a database
  get(readyPath) { call.respondHealth(withContext(Dispatchers.IO) { health.readiness() }) }
}

private suspend fun ApplicationCall.respondHealth(report: HealthReport) {
  response.header(HttpHeaders.CacheControl, "no-store")
  respondText(
    report.describe(),
    ContentType.Text.Plain,
    if (report.healthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
  )
}

/** The report as a few lines of text: `UP` or `DOWN`, the state, and each check's result. */
internal fun HealthReport.describe(): String = buildString {
  appendLine(if (healthy) "UP" else "DOWN")
  appendLine("state: $state")
  checks.forEach { (name, status) ->
    appendLine("$name: " + if (status is HealthStatus.Unhealthy) "DOWN (${status.reason})" else "UP")
  }
}

/**
 * A listener that serves only [health]'s routes, on a port of its own, e.g. one that only the platform's probes
 * reach. It isn't traced.
 *
 * Unlike other [KtorService]s, it is in the [DefaultPhase.Telemetry] phase, with what observes the application, so it
 * starts first and stops last: liveness is answered while the other services start, and readiness says the
 * application isn't ready while they start and stop.
 */
class HealthKtorService<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
  hostPort: HostPort,
  engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
  private val health: Health,
  private val livePath: String = KtorHealthConfig.DEFAULT_LIVE_PATH,
  private val readyPath: String = KtorHealthConfig.DEFAULT_READY_PATH,
  name: String = "health",
) : KtorService<TEngine, TConfiguration>(name, hostPort, engineFactory) {
  override fun ordering() = Ordering(DefaultPhase.Telemetry)

  override fun Application.module() {
    routing { healthRoutes(health, this@HealthKtorService.livePath, this@HealthKtorService.readyPath) }
  }
}
