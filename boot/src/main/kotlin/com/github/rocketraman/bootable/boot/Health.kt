package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import kotlin.time.Duration
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.kotlin.logger

/** Where the application is in its lifecycle, as [Health] tracks it. */
@Serializable
enum class LifecycleState {
  /** Initializing and starting the services. The state until every service has started. */
  Starting,

  /** Every service started: the application is running. */
  Running,

  /** Shutting down: draining, then stopping the services. A start that fails goes here too. */
  Stopping,

  /** Every service stopped. */
  Stopped,
}

/**
 * The result of a [HealthCheck].
 *
 * It is [Serializable], as `{"status": "UP"}` or `{"status": "DOWN", "reason": "…"}`.
 */
@Serializable(with = HealthStatusSerializer::class)
sealed interface HealthStatus {
  val healthy: Boolean

  data object Healthy : HealthStatus {
    override val healthy = true
  }

  data class Unhealthy(val reason: String) : HealthStatus {
    override val healthy = false
  }
}

/**
 * A check that the application is ready to take work, such as that its database answers, which [Health.readiness]
 * runs, while the application is running, whenever readiness is asked for.
 *
 * Readiness only: liveness runs no checks, since an application whose dependency is down isn't helped by restarting.
 *
 * A check runs on the thread asking for readiness, such as the request's, so it must return promptly, bounding its
 * own wait: a probe gives up after a second or so, by default. A check that throws is [HealthStatus.Unhealthy].
 */
interface HealthCheck {
  /** The check's name, in the readiness report, which defaults to the simple java class name. */
  fun name(): String = javaClass.simpleName

  fun check(): HealthStatus
}

/** A [HealthCheck] named [name], which runs [check]. */
fun HealthCheck(name: String, check: () -> HealthStatus): HealthCheck = object : HealthCheck {
  override fun name() = name
  override fun check() = check()
  override fun toString() = "HealthCheck($name)"
}

/**
 * Readiness settings, decoded from the `health.readiness` config section. Every setting has a default, so the
 * section is optional:
 *
 * ```
 * health {
 *   readiness {
 *     drainDelay = 5s
 *   }
 * }
 * ```
 */
data class ReadinessConfig(
  /**
   * How long to wait, once shutdown begins and readiness is down, before the services stop, so that load balancers
   * stop sending requests first. Kubernetes, for example, takes a few seconds to take a terminating pod out of its
   * endpoints, and a server that stops before then refuses the requests still routed to it. None by default.
   *
   * The wait comes before the shutdown timeout starts, so the platform's grace period, such as Kubernetes's
   * `terminationGracePeriodSeconds`, must cover both.
   */
  val drainDelay: Duration = Duration.ZERO,
)

/** Decodes [ReadinessConfig] from the `health.readiness` config section using the given [ConfigBinder]. */
fun readinessConfig(binder: ConfigBinder): ReadinessConfig = binder.config("health.readiness")

/**
 * A liveness answer: whether the application is [healthy], and its [state]. Liveness runs no checks, so it has none.
 *
 * It is [Serializable], with its status as `UP` or `DOWN`, and decodes back, such as in a client of the health routes:
 *
 * ```
 * { "status": "UP", "state": "Running" }
 * ```
 */
@Serializable
data class HealthLiveReport(
  @SerialName("status") @Serializable(with = UpDownSerializer::class) val healthy: Boolean,
  val state: LifecycleState,
)

/**
 * A readiness answer: whether the application is [healthy], its [state], and each check's result.
 *
 * It is [Serializable], with its status as `UP` or `DOWN`, and each check by name, so a consumer can check one:
 *
 * ```
 * {
 *   "status": "DOWN",
 *   "state": "Running",
 *   "checks": {
 *     "jdbc": { "status": "UP" },
 *     "db-scheduler": { "status": "DOWN", "reason": "paused" }
 *   }
 * }
 * ```
 *
 * The same form decodes back into a [HealthReadyReport], such as in a client of the health routes.
 */
@Serializable
data class HealthReadyReport(
  @SerialName("status") @Serializable(with = UpDownSerializer::class) val healthy: Boolean,
  val state: LifecycleState,
  /**
   * Each [HealthCheck]'s result, by name: empty unless the application is running, since the checks run only then.
   * Serialized even when empty, so a consumer always finds it.
   */
  @EncodeDefault val checks: Map<String, HealthStatus> = emptyMap(),
)

/**
 * The application's health, from Bootable's own lifecycle: a [LifecycleController] that tracks the application's
 * [LifecycleState], and answers liveness and readiness from it, such as for a Kubernetes probe or a load balancer's
 * health check.
 *
 * - [liveness] is healthy until the application has stopped. It runs no checks, so a dependency that is down
 *   doesn't get the application restarted, which wouldn't help it.
 * - [readiness] is healthy only while the application is running: once every service has started, and until shutdown
 *   begins. Then, it runs the [checks] too, and is healthy only if they all are.
 *
 * When shutdown begins, readiness goes down, and then, if the application was running, Health waits for the
 * [ReadinessConfig.drainDelay] before the services stop, so that load balancers stop routing to it first.
 *
 * Add it to the application's lifecycle controllers, and serve it, e.g. with `KtorHealth` from
 * `boot-server-http-ktor`:
 *
 * ```
 * val health = Health(setOf(JdbcHealthCheck(dataSource)), readinessConfig(binder))
 * Bootable(services, defaultLifecycleControllers() + health)
 * ```
 *
 * Kodein's `bootableModule` and Metro's `BootableDefaultBindings` add it for you, with the checks of the
 * `Set<HealthCheck>` they open.
 */
class Health(
  val checks: Set<HealthCheck> = emptySet(),
  val config: ReadinessConfig = ReadinessConfig(),
) : LifecycleController {

  @Volatile
  var state: LifecycleState = LifecycleState.Starting
    private set

  /** Healthy until the application has stopped. */
  fun liveness(): HealthLiveReport {
    val state = state
    return HealthLiveReport(state != LifecycleState.Stopped, state)
  }

  /** Healthy while the application is running, and every check is healthy. */
  fun readiness(): HealthReadyReport {
    val state = state
    if (state != LifecycleState.Running) return HealthReadyReport(false, state)
    val results = checks.associate { it.name() to it.runCheck() }
    // shutdown may have begun while the checks ran
    return HealthReadyReport(results.values.all { it.healthy } && this.state == LifecycleState.Running, state, results)
  }

  override fun aroundAppStart(serviceContext: ServiceContext, timeline: StartupTimeline, start: () -> Unit) {
    start()
    // a start that throws leaves the state Starting, until the shutdown that follows
    if (state == LifecycleState.Starting) state = LifecycleState.Running
  }

  override fun appShutdown(serviceContext: ServiceContext, maxShutdownTime: Long, exitProcess: (exitCode: Int) -> Unit) {
    val wasRunning = state == LifecycleState.Running
    state = LifecycleState.Stopping
    if (wasRunning && config.drainDelay.isPositive()) {
      log.info { "==> Not ready; draining for ${config.drainDelay} before stopping the services…" }
      Thread.sleep(config.drainDelay.inWholeMilliseconds)
    }
  }

  override fun appCleanup() {
    state = LifecycleState.Stopped
  }

  /**
   * The lowest, so readiness goes down, and the drain runs, before any other controller shuts down, such as
   * [AppStartStopLifecycleController] stopping the services; and the application is running only once the rest of
   * the start, within every other controller's [aroundAppStart], has completed.
   */
  override fun priority() = Int.MIN_VALUE

  @Suppress("TooGenericExceptionCaught")
  private fun HealthCheck.runCheck(): HealthStatus = try {
    check()
  } catch (e: Exception) {
    log.debug(e) { "Health check ${name()} failed" }
    HealthStatus.Unhealthy(e.message ?: e.javaClass.name)
  }

  companion object {
    private val log = logger()
  }
}
