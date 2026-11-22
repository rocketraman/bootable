package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Ordering
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.logging.log4j.kotlin.logger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.TimeSource

// by name, rather than logger(), whose name resolution would need native-image metadata for the calling class
internal val log = logger("com.github.rocketraman.bootable.periodic.PeriodicAppService")

/**
 * An [AppService] that runs [work] periodically, in the background, while the application runs: the loop that
 * sends pending notifications, or sweeps expired records, that an application would otherwise write by hand.
 *
 * - It runs [work] every [PeriodicConfig.interval], a fixed delay after the previous run ends, on a coroutine of its
 *   own, in the service's scope. A run that throws is handed to the [onFailure] handler, which logs it by default, and
 *   the loop carries on, unless [PeriodicConfig.maxConsecutiveFailures] runs in a row have failed.
 * - Before each run, it asks its [gate], and skips the run when the gate is closed: use [PeriodicGate.leader] so that
 *   only one instance of the application runs it. If the gate closes while a run is in progress, such as when this
 *   instance stops leading, it cancels the run, which isn't counted as a failure. Cancellation is cooperative: [work]
 *   that blocks, such as JDBC calls, stops only at its next suspension point, unless it blocks in `runInterruptible`.
 * - A disabled loop ([PeriodicConfig.enabled]) starts nothing.
 * - On shutdown, it starts no further run, and waits up to [PeriodicConfig.shutdownTimeout] for a run in progress to
 *   finish, before cancelling it.
 * - Each [interceptor][PeriodicRunInterceptor] runs around each run: [periodicTracing], for one, traces each run as a span.
 * - It notes when each run started, and when one last succeeded and failed, by [clock], e.g. for a health check that
 *   the loop isn't stuck.
 *
 * It is in the [DefaultPhase.Serving] phase by default, as something that brings work into the application, so it
 * starts after the services its work uses, and stops before them.
 *
 * ```
 * PeriodicAppService("notifications", binder.config("notifications.loop"), PeriodicGate.leader(leaderElection)) {
 *   notifications.sendPending()
 * }
 * ```
 *
 * [PeriodicServices] holds the settings the application's loops share, such as their gate and interceptors, for DI.
 *
 * @param name the service's name, in its log messages, and the name of the span [periodicTracing] traces each run in.
 * @param dispatcher where [work] runs. [work] that blocks, such as JDBC calls, should switch to `Dispatchers.IO` for
 *   the calls that block, or pass it here.
 */
class PeriodicAppService(
  private val name: String,
  private val config: PeriodicConfig,
  private val gate: PeriodicGate = PeriodicGate.Always,
  private val clock: Clock = Clock.System,
  private val interceptors: List<PeriodicRunInterceptor> = emptyList(),
  private val onFailure: PeriodicFailureHandler = PeriodicFailureHandler.Log,
  private val ordering: Ordering = Ordering(DefaultPhase.Serving),
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val work: suspend () -> Unit,
) : AppService {

  private var loop: Job? = null
  private var stopping = CompletableDeferred<Unit>()

  /** When the last run started, or null if none has. */
  @Volatile
  var lastStarted: Instant? = null
    private set

  /** When the last run that succeeded ended, or null if none has. */
  @Volatile
  var lastSucceeded: Instant? = null
    private set

  /** When the last run that failed ended, or null if none has. */
  @Volatile
  var lastFailed: Instant? = null
    private set

  /** How many runs in a row have failed, up to the last run: 0 if it succeeded. */
  @Volatile
  var consecutiveFailures: Int = 0
    private set

  /** Whether the loop is running: from start until it stops, at shutdown or the failure limit. */
  val isRunning: Boolean get() = loop?.isActive == true

  override fun name() = name

  override fun ordering() = ordering

  override suspend fun start(scope: CoroutineScope) {
    if (!config.enabled) {
      log.info { "Periodic service $name is disabled" }
      return
    }
    stopping = CompletableDeferred()
    loop = scope.launch(dispatcher + CoroutineName(name)) { loop() }
    log.info { "Periodic service $name started, every ${config.interval}" }
  }

  override suspend fun shutdown() {
    val loop = loop ?: return
    stopping.complete(Unit)
    if (withTimeoutOrNull(config.shutdownTimeout) { loop.join() } == null) {
      log.warn { "Periodic service $name's run didn't finish within ${config.shutdownTimeout}; cancelling it" }
      loop.cancelAndJoin()
    }
  }

  private suspend fun loop() {
    if (pause(config.initialDelay)) return
    while (true) {
      if (gateOpen()) {
        if (!runOnce()) {
          val limit = config.maxConsecutiveFailures
          if (limit != null && consecutiveFailures >= limit) {
            failureLimitReached(limit)
            return
          }
        }
      } else {
        log.debug { "Periodic service $name's gate is closed; skipping this run" }
      }
      if (pause(config.interval)) return
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private fun gateOpen(): Boolean = try {
    gate.isOpen()
  } catch (e: Exception) {
    log.warn(e) { "Periodic service $name's gate failed; skipping this run" }
    false
  }

  /** Waits for [duration], or until shutdown begins, and returns whether it has. */
  private suspend fun pause(duration: Duration): Boolean {
    if (stopping.isCompleted) return true
    return withTimeoutOrNull(duration) { stopping.await() } != null || stopping.isCompleted
  }

  /**
   * Runs [work] once, through the interceptors, cancelling it if the gate closes, and returns false if it failed: a
   * run the gate cancelled didn't.
   */
  @Suppress("TooGenericExceptionCaught")
  private suspend fun runOnce(): Boolean {
    lastStarted = clock.now()
    val mark = TimeSource.Monotonic.markNow()
    val timed: suspend () -> Unit = config.runTimeout?.let { timeout -> { withTimeout(timeout) { work() } } } ?: work
    val run = interceptors.foldRight(timed) { interceptor, next -> { interceptor.intercept(name, next) } }
    var gateClosed = false
    return try {
      coroutineScope {
        val running = async { run() }
        val watch = launch {
          gate.awaitClosed()
          gateClosed = true
          running.cancel("Periodic service $name's gate closed")
        }
        try {
          running.await()
        } finally {
          watch.cancel()
        }
      }
      lastSucceeded = clock.now()
      consecutiveFailures = 0
      log.debug { "Periodic service $name ran in ${mark.elapsedNow()}" }
      true
    } catch (e: Throwable) {
      // this coroutine was cancelled, at shutdown: not the run's failure
      if (e is CancellationException && !currentCoroutineContext().isActive) throw e
      if (e is VirtualMachineError) throw e
      if (e is CancellationException && gateClosed) {
        log.warn { "Periodic service $name's gate closed during a run, after ${mark.elapsedNow()}; cancelled it" }
        return true
      }
      lastFailed = clock.now()
      consecutiveFailures++
      try {
        onFailure.onFailure(name, e, consecutiveFailures)
      } catch (handlerError: Exception) {
        log.error(handlerError) { "Periodic service $name's failure handler failed, handling:\n${e.stackTraceToString()}" }
      }
      false
    }
  }

  private fun failureLimitReached(limit: Int) {
    when (config.onFailureLimit) {
      FailureLimitAction.StopLoop ->
        log.error { "Periodic service $name stopped: its last $limit runs failed" }
      // failing the loop fails the service's scope, which stops the application
      FailureLimitAction.Shutdown ->
        throw PeriodicFailureLimitException("Periodic service $name's last $limit runs failed; stopping the application")
    }
  }

  override fun toString() = "PeriodicAppService($name)"
}

/** Thrown by a [PeriodicAppService]'s loop when [FailureLimitAction.Shutdown] stops the application. */
class PeriodicFailureLimitException(message: String) : IllegalStateException(message)
