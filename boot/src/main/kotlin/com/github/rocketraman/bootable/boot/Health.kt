package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.kotlin.logger

/** Where the application is in its lifecycle, as [HealthLifecycleController] tracks it. */
@Serializable
enum class LifecycleState {
  /** Initializing and starting the services. The state until the application's start has completed. */
  Starting,

  /**
   * The application's whole start completed, including every lifecycle controller's code around it, such as
   * `ServiceStartTracing`'s span: the application is running.
   */
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
 * A check that the application is ready to take work, such as that its database answers, which
 * [HealthLifecycleController.readiness] runs, while the application is running, whenever readiness is asked for.
 *
 * Readiness only: liveness runs no checks, since an application whose dependency is down isn't helped by restarting.
 *
 * [HealthLifecycleController] bounds each check by [ReadinessConfig.checkTimeout]: a check that hasn't answered by
 * then is [HealthStatus.Unhealthy], and one that throws is too. A check that suspends is cancelled at the timeout;
 * one that blocks, which it does in `withContext(Dispatchers.IO)`, can't be, so readiness answers without it, and the
 * check finishes later on its own thread, which it bounds itself. While it runs, readiness doesn't run it again, but
 * awaits the same run.
 */
interface HealthCheck {
  /** The check's name, in the readiness report, which defaults to the simple java class name. */
  fun name(): String = javaClass.simpleName

  suspend fun check(): HealthStatus
}

/** A [HealthCheck] named [name], which runs [check]. */
fun HealthCheck(name: String, check: suspend () -> HealthStatus): HealthCheck = object : HealthCheck {
  override fun name() = name
  override suspend fun check() = check()
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
 *     checkTimeout = 800ms
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

  /**
   * How long readiness waits for each check, which all run at once, after which the check is unhealthy. Below
   * Kubernetes's default probe timeout of a second, so a slow dependency fails the probe with a reason, rather than
   * time it out.
   */
  val checkTimeout: Duration = 800.milliseconds,
)

/** Decodes [ReadinessConfig] from the `health.readiness` config section using the given [ConfigBinder]. */
fun readinessConfig(binder: ConfigBinder): ReadinessConfig = binder.config("health.readiness")

/**
 * A liveness answer: whether the application is [healthy], its [state], and each service's state. Liveness runs no
 * checks, so it has none.
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
  /** Each service's state, by name, in the order of the start plan: empty, and left out, until the application starts. */
  val services: Map<String, ServiceState> = emptyMap(),
)

/**
 * A readiness answer: whether the application is [healthy], its [state], each check's result, and each service's state.
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
  /** Each service's state, by name, in the order of the start plan: empty, and left out, until the application starts. */
  val services: Map<String, ServiceState> = emptyMap(),
)

/**
 * The application's health, from Bootable's own lifecycle: a [LifecycleController] that tracks the application's
 * [LifecycleState], and answers liveness and readiness from it, such as for a Kubernetes probe or a load balancer's
 * health check.
 *
 * - [liveness] is healthy until the application has stopped. It runs no checks, so a dependency that is down
 *   doesn't get the application restarted, which wouldn't help it.
 * - [readiness] is healthy only while the application is running: once its start has completed, and until shutdown
 *   begins. Then, it runs the [checks] too, and is healthy only if they all are.
 *
 * When shutdown begins, readiness goes down, and then, if the application was running, HealthLifecycleController waits
 * for the [ReadinessConfig.drainDelay] before the services stop, so that load balancers stop routing to it first.
 *
 * One with no checks is among the [defaultLifecycleControllers]; replace it with [withHealthLifecycleController] to
 * give it checks, and serve it, e.g. with `KtorHealth` from `boot-server-http-ktor`:
 *
 * ```
 * val health = HealthLifecycleController(setOf(JdbcHealthCheck(dataSource)), readinessConfig(binder))
 * Bootable(services, defaultLifecycleControllers().withHealthLifecycleController(health))
 * ```
 *
 * Kodein's `bootableModule` and Metro's `BootableDefaultBindings` add it for you, with the checks of the
 * `Set<HealthCheck>` they open, as Koin's `bootableModule` does with every `HealthCheck` definition, and Inara's
 * `BootableModule` with its `@HealthChecks` members.
 */
class HealthLifecycleController(
  val checks: Set<HealthCheck> = emptySet(),
  val config: ReadinessConfig = ReadinessConfig(),
) : LifecycleController {

  private val stateFlow = MutableStateFlow(LifecycleState.Starting)

  /** The application's state, to await one, rather than poll [state]. */
  val states: StateFlow<LifecycleState> = stateFlow.asStateFlow()

  val state: LifecycleState get() = stateFlow.value

  @Volatile
  private var serviceStates: StateFlow<Map<AppService, ServiceState>>? = null

  /** The checks running, each awaited by every readiness that asks while it runs. */
  private val running = ConcurrentHashMap<HealthCheck, Deferred<HealthStatus>>()

  /** Where the checks run: blocking checks block a thread of [Dispatchers.IO], rather than the caller's. */
  private val checkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("health-checks"))

  /** Healthy until the application has stopped. */
  fun liveness(): HealthLiveReport {
    val state = state
    return HealthLiveReport(state != LifecycleState.Stopped, state, services = services())
  }

  /** Healthy while the application is running, and every check is healthy, within [ReadinessConfig.checkTimeout]. */
  suspend fun readiness(): HealthReadyReport {
    val state = state
    if (state != LifecycleState.Running) return HealthReadyReport(false, state, services = services())
    val results = coroutineScope {
      checks.map { check -> async { check.name() to check.runCheck() } }.awaitAll().toMap()
    }
    // shutdown may have begun while the checks ran
    val healthy = results.values.all { it.healthy } && this.state == LifecycleState.Running
    return HealthReadyReport(healthy, state, results, services())
  }

  private fun services(): Map<String, ServiceState> =
    serviceStates?.value?.entries?.associate { (service, state) -> service.name to state }.orEmpty()

  override suspend fun aroundAppStart(
    serviceContext: ServiceContext,
    timeline: StartupTimeline,
    start: suspend () -> Unit,
  ) {
    serviceStates = serviceContext.states
    start()
  }

  // a start that fails, or that a stop cancels, never calls it, leaving the state Starting, until the shutdown that
  // follows
  override suspend fun appRunning() {
    stateFlow.compareAndSet(LifecycleState.Starting, LifecycleState.Running)
  }

  override suspend fun appShutdown(serviceContext: ServiceContext, maxShutdownTime: Duration) {
    val wasRunning = state == LifecycleState.Running
    stateFlow.value = LifecycleState.Stopping
    if (wasRunning && config.drainDelay.isPositive()) {
      log.info { "==> Not ready; draining for ${config.drainDelay} before stopping the services…" }
      delay(config.drainDelay)
    }
  }

  override suspend fun appCleanup() {
    stateFlow.value = LifecycleState.Stopped
    checkScope.cancel()
  }

  /**
   * The lowest, so readiness goes down, and the drain runs, before any other controller shuts down, such as
   * [AppStartStopLifecycleController] stopping the services; and the application is running only once every other
   * controller's [appRunning] has completed.
   */
  override fun priority() = Int.MIN_VALUE

  /** Runs this check, or awaits its run already running, within the check timeout. */
  private suspend fun HealthCheck.runCheck(): HealthStatus {
    val run = running.compute(this) { _, earlier ->
      earlier?.takeIf { it.isActive } ?: checkScope.async { checkCatching() }
    }!!
    return withTimeoutOrNull(config.checkTimeout) { run.await() }
      ?: HealthStatus.Unhealthy("no answer within ${config.checkTimeout}")
  }

  @Suppress("TooGenericExceptionCaught")
  private suspend fun HealthCheck.checkCatching(): HealthStatus = try {
    check()
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    log.debug(e) { "Health check ${name()} failed" }
    HealthStatus.Unhealthy(e.message ?: e.javaClass.name)
  }

  companion object {
    private val log = logger()
  }
}

/**
 * These lifecycle controllers, with [health] in place of any [HealthLifecycleController] among them, such as the one of
 * the [defaultLifecycleControllers]: an application has one.
 */
fun Set<LifecycleController>.withHealthLifecycleController(health: HealthLifecycleController): Set<LifecycleController> =
  filterNot { it is HealthLifecycleController }.toSet() + health
