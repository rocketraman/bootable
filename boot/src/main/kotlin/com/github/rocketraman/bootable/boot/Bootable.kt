package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.common.threadStacks
import com.github.rocketraman.bootable.logging.log4j2.LoggingType
import com.github.rocketraman.bootable.logging.log4j2.loggingInit
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.apache.logging.log4j.kotlin.logger

/**
 * The runnable core of Bootable — an ordinary Kotlin class with no dependency-injection annotations. Construct it
 * with your services and lifecycle controllers (usually via the [boot] convenience function) however you
 * assembled them: by hand, or with whatever DI framework you prefer.
 *
 * The services start in the order of their [AppService.ordering]: phase by phase, in the order of [startOrder]'s
 * phases, and within each phase after the providers of the milestones each service waits for. [tiebreaker] orders
 * the services those leave unordered. The start plan is computed and logged before any lifecycle controller
 * runs, so an order that can't be met fails the boot before anything has started (see [startPlan]). If starting
 * fails, the services that had started are shut down, in reverse, before [run] returns.
 *
 * The lifecycle runs under structured concurrency: [run] starts the services, waits for a [stop], and stops them,
 * and each service's background work runs in a scope of its own, a child of the application's, which doesn't
 * outlive [run] (see `AppService.start(scope)`).
 *
 * ## Scopes
 *
 * ```
 * Bootable.run()           starts, waits for a stop, stops: on the caller's thread, the main thread under boot
 * └─ application job       SupervisorJob, on Dispatchers.Default: supervises the services, relative to each other
 *    ├─ service A's job    Job (ScopeFailurePolicy.StopApplication): failure cancels A's other coroutines, and fails A
 *    │  ├─ coroutine
 *    │  └─ coroutine
 *    └─ service B's job    SupervisorJob (ScopeFailurePolicy.LogAndContinue): a coroutine fails alone
 *       ├─ coroutine
 *       └─ coroutine
 * ```
 *
 * - Between services, failures are always supervised: one service's failing job doesn't cancel another's. Bootable
 *   reacts instead, as the service's [AppService.scopeFailurePolicy] says, by default by stopping the application in
 *   order, each service's `shutdown` running while what it depends on still runs.
 * - Within a service, a failure isn't supervised by default: a service with one coroutine dead is likely half
 *   broken, so the whole service fails. [ScopeFailurePolicy.LogAndContinue] makes the service's job a `SupervisorJob`
 *   instead, for a service whose coroutines are independent of each other.
 * - Once a service has stopped, Bootable cancels its job and waits for its coroutines. Once every service has
 *   stopped, it cancels the application job too, and waits briefly for anything left in it, which no service owns.
 */
class Bootable(
  private val appServices: Set<AppService>,
  private val lifecycleControllers: Set<LifecycleController> = defaultLifecycleControllers(),
  val startOrder: StartOrder = DefaultStartOrder,
  val tiebreaker: StartOrder.Tiebreaker = StartOrder.Tiebreaker.ByName,
) {

  /** The exit code of the first stop asked for: its completion is the stop. */
  private val stopRequested = CompletableDeferred<Int>()
  private val ran = AtomicBoolean(false)
  /** Completed once [run] has stopped the application, for the shutdown hook to wait for. */
  private val finished = CompletableDeferred<Unit>()

  private val states = MutableStateFlow<Map<AppService, ServiceState>>(emptyMap())
  /** The services started, and not yet stopped, with their scopes. */
  private val started = ConcurrentHashMap<AppService, CompletableJob>()
  private val shutdownWatchdog = Watchdog()

  private lateinit var serviceContext: ServiceContext
  private lateinit var applicationJob: CompletableJob
  private lateinit var applicationScope: CoroutineScope
  private var maxShutdownTime: Duration = 30.seconds
  private var haltGrace: Duration = HALT_GRACE

  companion object {
    private val log = logger()

    /** How long the watchdog waits, after the shutdown timeout, for a stop that ignores cancellation, before it halts. */
    internal val HALT_GRACE = 5.seconds
  }

  /**
   * Asks the application to stop, with [exitCode]. It never blocks, and can be called from any thread, at any time,
   * even before [run]: only the first stop asked for, this way or any other, counts.
   *
   * A stop during the start cancels the start, and stops the services that had started.
   */
  fun stop(exitCode: Int = 0) {
    if (stopRequested.complete(exitCode)) log.info { "=> Stop requested, with exit code $exitCode" }
  }

  /**
   * Runs the application until it stops, and returns its exit code: that of the first [stop], or `-1` if a service
   * failed to start, or `1` if a service's background work failed, or the stop didn't complete within
   * [maxShutdownTime]. Cancelling the coroutine that runs it stops the application too, and then rethrows.
   *
   * It neither exits nor halts the process, nor installs a shutdown hook: [boot] does those. A [Bootable] runs once.
   */
  suspend fun run(maxShutdownTime: Duration = 30.seconds): Int =
    run(StartupTimeline(TimeSource.Monotonic.markNow()), maxShutdownTime)

  /**
   * [run], with [boot], the timeline so far: with the [boot] function, its first stages; this adds the planning and
   * initialization. [halt] is what the shutdown watchdog does last: [boot] halts the process, while anything else that
   * runs an application, such as a test, mustn't, so the default only logs.
   */
  internal suspend fun run(
    boot: StartupTimeline,
    maxShutdownTime: Duration,
    halt: ((exitCode: Int) -> Unit)? = null,
    haltGrace: Duration = HALT_GRACE,
  ): Int {
    check(ran.compareAndSet(false, true)) { "A Bootable runs once" }
    this.haltGrace = haltGrace
    shutdownWatchdog.onExpiry = { expired(halt) }
    try {
      return runApplication(boot, maxShutdownTime)
    } finally {
      finished.complete(Unit)
    }
  }

  private suspend fun runApplication(boot: StartupTimeline, maxShutdownTime: Duration): Int {
    this.maxShutdownTime = maxShutdownTime
    val startupMark = boot.start
    appPlan()
    val planned = boot.then(StartupTimeline.PLAN_START)

    log.info("=> Initializing (${startupMark.elapsedNow()})…")
    appInit()
    val timeline = planned.then(StartupTimeline.INITIALIZE)

    applicationJob = SupervisorJob()
    // a service's failure is handled as its scope completes (see serviceStart): this only keeps it from being reported
    // as uncaught too
    val handled = CoroutineExceptionHandler { context, e -> log.debug(e) { "Coroutine ${context[CoroutineName]} failed" } }
    applicationScope = CoroutineScope(applicationJob + Dispatchers.Default + CoroutineName("application") + handled)

    var cancellation: CancellationException? = null
    var runningMark: TimeMark? = null
    @Suppress("TooGenericExceptionCaught")
    try {
      log.info("=> Starting (${startupMark.elapsedNow()})…")
      if (appStart(timeline, maxShutdownTime)) {
        log.info("=> Running (${startupMark.elapsedNow()})")
        runningMark = TimeSource.Monotonic.markNow()
        stopRequested.await()
      }
    } catch (e: CancellationException) {
      if (currentCoroutineContext().isActive) {
        // not run's own cancellation, but a start that threw it, such as a service's own timeout
        log.fatal(e) { "Failed to start application, stopping the services that started…" }
        stop(-1)
      } else {
        cancellation = e
        stop(1)
      }
    } catch (e: Exception) {
      log.fatal(e) { "Failed to start application, stopping the services that started…" }
      stop(-1)
    }

    val stoppedFully = withContext(NonCancellable) { appStop(startupMark, maxShutdownTime) }
    runningMark?.let { log.debug { "Total uptime was ${it.elapsedNow()}." } }
    cancellation?.let { throw it }
    // completed: every way out of the start or the wait above asks for a stop
    return if (stoppedFully) stopRequested.await() else 1
  }

  /** Starts the application, and returns whether it is running, or false if a stop cancelled the start. */
  private suspend fun appStart(timeline: StartupTimeline, maxShutdownTime: Duration): Boolean = coroutineScope {
    // the controllers learn the application is running within the start, so a stop meanwhile cancels that too
    val start = async {
      aroundAppStart(timeline)
      appRunning()
    }
    select {
      start.onAwait { true }
      stopRequested.onAwait {
        log.info { "==> Cancelling the start…" }
        // a start blocked in code that ignores cancellation gets as long to give up as a stop does
        shutdownWatchdog.arm(maxShutdownTime + haltGrace)
        start.cancelAndJoin()
        shutdownWatchdog.disarm()
        false
      }
    }
  }

  /** Stops the application, and returns whether every service that started has stopped. */
  private suspend fun appStop(startupMark: TimeMark, maxShutdownTime: Duration): Boolean {
    log.info("=> Shutting down (${startupMark.elapsedNow()})")

    val shutdownMark = TimeSource.Monotonic.markNow()
    // the watchdog is armed by the first service's stop, after the drain
    try {
      appShutdown(maxShutdownTime)
      appCleanup()
    } finally {
      shutdownWatchdog.disarm()
    }

    val notStopped = started.keys.toList()
    if (notStopped.isNotEmpty()) {
      log.error { "Services didn't stop: ${notStopped.map { it.name() }}\n${diagnostics()}" }
    }
    // anything left in the application's scope, which no service owns
    applicationJob.cancel()
    withTimeoutOrNull(haltGrace) { applicationJob.join() }

    log.debug { "Shut down application in ${shutdownMark.elapsedNow()}." }
    log.info("=> Stopped")
    return notStopped.isEmpty()
  }

  private fun appPlan() {
    val plan = phasedStartPlan(appServices, startOrder, tiebreaker)
    val shuffled =
      (tiebreaker as? StartOrder.Tiebreaker.Shuffled)?.let { " (ties shuffled, seed ${it.seed})" }.orEmpty()
    log.info { "Start plan$shuffled: ${plan.describe()}" }
    val startPlan = plan.flatMap { it.services }
    states.value = startPlan.associateWith { ServiceState.Pending }
    serviceContext = ServiceContext(appServices, startPlan, ::serviceStart, ::serviceShutdown, states.asStateFlow())
  }

  private fun appInit() {
    lifecycleControllers.sortedByDescending { it.priority() }.forEach {
      it.appInit()
    }
  }

  private suspend fun aroundAppStart(timeline: StartupTimeline) {
    val start = lifecycleControllers.sortedBy { it.priority() }
      .fold<LifecycleController, suspend () -> Unit>(::startControllers) { inner, controller ->
        { controller.aroundAppStart(serviceContext, timeline, inner) }
      }
    start()
  }

  /** Tells the controllers the application is running, unless it began to stop meanwhile. */
  private suspend fun appRunning() {
    lifecycleControllers.sortedByDescending { it.priority() }.forEach {
      if (!stopRequested.isCompleted) {
        it.appRunning()
      }
    }
  }

  private suspend fun startControllers() {
    lifecycleControllers.sortedByDescending { it.priority() }.forEach {
      if (!stopRequested.isCompleted) {
        it.appStart(serviceContext, ::stop)
      }
    }
  }

  private suspend fun appShutdown(maxShutdownTime: Duration) {
    lifecycleControllers.sortedBy { it.priority() }.forEach {
      runCatchingNonCancellation({ e -> log.warn(e) { "Error shutting down controller ${it.name()}, ignoring…" } }) {
        it.appShutdown(serviceContext, maxShutdownTime)
      }
    }
  }

  private suspend fun appCleanup() {
    lifecycleControllers.sortedBy { it.priority() }.forEach {
      runCatchingNonCancellation({ e -> log.warn(e) { "Error cleaning up controller ${it.name()}, ignoring…" } }) {
        it.appCleanup()
      }
    }
  }

  private suspend fun serviceStart(appService: AppService) {
    // let a stop asked for in the meantime cancel the start, before the next service starts
    yield()
    val votedNo = lifecycleControllers.filter { it.serviceStartVote(appService) == ServiceStartVote.NO }
    if (votedNo.isNotEmpty()) {
      log.warn { "==> Skipping start of ${appService.name()}, these controllers voted NO: ${votedNo.map { it.name() }}…" }
      setState(appService, ServiceState.Skipped)
      return
    }

    log.info { "==> Starting ${appService.name()}…" }
    setState(appService, ServiceState.Starting)
    val policy = appService.scopeFailurePolicy
    val serviceJob = if (policy.supervise) SupervisorJob(applicationJob) else Job(applicationJob)
    @Suppress("TooGenericExceptionCaught")
    try {
      val start = lifecycleControllers.sortedBy { it.priority() }
        .fold<LifecycleController, suspend () -> Unit>({ startService(appService, serviceJob, policy) }) { inner, controller ->
          { controller.aroundServiceStart(appService, inner) }
        }
      start()
    } catch (e: Throwable) {
      serviceJob.cancel()
      val cancelled = e is CancellationException && !currentCoroutineContext().isActive
      setState(appService, if (cancelled) ServiceState.Pending else ServiceState.Failed)
      throw e
    }
    started[appService] = serviceJob
    setState(appService, ServiceState.Running)
    // a scope that doesn't supervise fails with its coroutine; one that does reports each failure to its handler
    serviceJob.invokeOnCompletion { cause ->
      if (cause != null && cause !is CancellationException) policy.onFailure(this, appService, null, cause)
    }
  }

  private suspend fun startService(appService: AppService, serviceJob: Job, policy: ScopeFailurePolicy) {
    var context = applicationScope.coroutineContext + serviceJob + CoroutineName(appService.name())
    if (policy.supervise) {
      context += CoroutineExceptionHandler { coroutine, e ->
        policy.onFailure(this, appService, coroutine[CoroutineName]?.name, e)
      }
    }
    appService.start(CoroutineScope(context))
  }

  /** Marks [appService] failed, for a [ScopeFailurePolicy] that fails it. */
  internal fun serviceFailed(appService: AppService) = setState(appService, ServiceState.Failed)

  private suspend fun serviceShutdown(appService: AppService) {
    val serviceJob = started[appService]
    if (serviceJob == null) {
      log.info { "==> Stopping ${appService.name()}… not started, ignoring shutdown" }
      return
    }
    // the first service to stop arms the watchdog: after the drain, as the shutdown timeout is
    shutdownWatchdog.armOnce(maxShutdownTime + haltGrace)
    log.info { "==> Stopping ${appService.name()}…" }
    setState(appService, ServiceState.Stopping)
    runCatchingNonCancellation({ e -> log.warn(e) { "Error stopping ${appService.name()}, ignoring…" } }) {
      appService.shutdown()
    }
    serviceJob.cancelAndJoin()
    started.remove(appService)
    setState(appService, ServiceState.Stopped)
  }

  private fun setState(appService: AppService, state: ServiceState) {
    states.update { it + (appService to state) }
  }

  /** The watchdog fired: the stop is blocked in code that ignores cancellation. */
  private fun expired(halt: ((exitCode: Int) -> Unit)?) {
    log.error { "Application did not shut down normally${if (halt != null) ", halting" else ""}.\n${diagnostics()}" }
    halt?.invoke(1)
  }

  /** Each service's state, the coroutines still running, and the threads' stacks, to say why a stop is stuck. */
  private fun diagnostics(): String = buildString {
    appendLine("Services: ${states.value.entries.joinToString { (service, state) -> "${service.name()}=$state" }}")
    if (::applicationJob.isInitialized) {
      appendLine("Coroutines still running:")
      appendJobTree(applicationJob, "  ")
    }
    debugProbesDump()?.let { appendLine("Coroutine stacks:").appendLine(it) }
    append("Thread stacks: ").append(threadStacks())
  }

  private fun StringBuilder.appendJobTree(job: Job, indent: String) {
    job.children.forEach { child ->
      appendLine("$indent$child")
      appendJobTree(child, "$indent  ")
    }
  }

  /** The coroutines' stacks, if the application installed kotlinx-coroutines-debug's `DebugProbes`. */
  @Suppress("TooGenericExceptionCaught")
  private fun debugProbesDump(): String? = try {
    val probes = Class.forName("kotlinx.coroutines.debug.DebugProbes")
    val instance = probes.getField("INSTANCE").get(null)
    if (probes.getMethod("isInstalled").invoke(instance) != true) null
    else ByteArrayOutputStream().also { out ->
      PrintStream(out).use { probes.getMethod("dumpCoroutines", PrintStream::class.java).invoke(instance, it) }
    }.toString()
  } catch (e: Exception) {
    null
  }

  /**
   * Runs [block], passing its exceptions to [onException], except a cancellation of the calling coroutine, such as the
   * shutdown timeout's, which propagates.
   */
  @Suppress("TooGenericExceptionCaught")
  private suspend inline fun runCatchingNonCancellation(onException: (Throwable) -> Unit, block: () -> Unit) {
    try {
      block()
    } catch (e: Throwable) {
      if (e is CancellationException && !currentCoroutineContext().isActive) throw e
      if (e is VirtualMachineError) throw e
      onException(e)
    }
  }

  /** Waits for [run] to have stopped the application, for up to [timeout]. For the shutdown hook. */
  internal fun awaitFinished(timeout: Duration) = runBlocking { withTimeoutOrNull(timeout) { finished.await() } }
}

/**
 * A one-shot timer on a daemon thread of its own, rather than a coroutine, so that it fires even when the thread the
 * lifecycle runs on is blocked.
 */
private class Watchdog {
  @Volatile var onExpiry: () -> Unit = {}
  private var thread: Thread? = null

  @Synchronized
  fun arm(after: Duration) {
    disarm()
    thread = thread(isDaemon = true, name = "bootable-shutdown-watchdog") {
      try {
        Thread.sleep(after.inWholeMilliseconds)
      } catch (e: InterruptedException) {
        return@thread
      }
      onExpiry()
    }
  }

  @Synchronized
  fun armOnce(after: Duration) {
    if (thread == null) arm(after)
  }

  @Synchronized
  fun disarm() {
    thread?.interrupt()
    thread = null
  }
}

/**
 * Bootable's default lifecycle controllers: [AppStartStopLifecycleController] (start services on boot, stop them
 * on shutdown), [StopSignalHandlerLifecycleController] (clean shutdown on HUP/INT/TERM), and a
 * [HealthLifecycleController] with no checks (the application's liveness and readiness). Replace it with
 * [withHealthLifecycleController] to give it readiness checks or a drain delay, and to serve it:
 *
 * ```
 * val health = HealthLifecycleController(setOf(JdbcHealthCheck(dataSource)), readinessConfig(binder))
 * Bootable(services, defaultLifecycleControllers().withHealthLifecycleController(health))
 * ```
 *
 * Pass a different set to the [Bootable] you build in [boot] to run a custom lifecycle — for example
 * starting/stopping services on command from an RPC or web request rather than at boot.
 */
fun defaultLifecycleControllers(): Set<LifecycleController> = setOf(
  AppStartStopLifecycleController(),
  StopSignalHandlerLifecycleController(),
  HealthLifecycleController(),
)

/**
 * The convenience entry point: initialize logging, then build and boot a [Bootable], and exit the process with its
 * exit code once it stops.
 *
 * Bootable embeds no dependency-injection framework — you assemble the application inside the lambda however you
 * like (plain Kotlin, or Metro/Inara/Koin/etc. if you prefer), and return the [Bootable] to run. The lambda runs
 * *after* logging is initialized, so config loading and component construction log correctly:
 *
 * ```
 * fun main() = boot {
 *   val config = hopliteConfigBinder()
 *   Bootable(
 *     appServices = setOf(ExampleServer(config.config("server"), config.config("hello"))),
 *     // this uses the default lifecycle controllers, but lifecycleControllers can be injected if necessary
 *   )
 * }
 * ```
 *
 * The application runs on the main thread, in `runBlocking`. A JVM shutdown hook stops it, should the JVM begin to
 * exit some other way than through the application's own stop, and waits for it to stop. If the stop doesn't complete
 * within [maxShutdownTime], and then shortly after, because a service blocks in code that ignores cancellation, the
 * process halts.
 *
 * @param maxShutdownTime how long the services have to stop, once shutdown begins, before they are cancelled.
 */
fun boot(
  maxShutdownTime: Duration = 30.seconds,
  loggingType: LoggingType? = null,
  bootable: () -> Bootable,
) {
  val booting = StartupTimeline(TimeSource.Monotonic.markNow())

  loggingInit(loggingType = loggingType)
  val log = logger("Bootable")

  val exitCode = try {
    log.info("=> Creating application")
    val application = bootable()
    // shouldn't need this if a signal handler is installed, but install this shutdown hook as a fallback
    val shutdownHook = Thread {
      application.stop()
      application.awaitFinished(maxShutdownTime + Bootable.HALT_GRACE)
    }
    Runtime.getRuntime().addShutdownHook(shutdownHook)
    val exitCode = runBlocking {
      application.run(booting.then(StartupTimeline.INITIALIZE_JVM_CLASSES), maxShutdownTime, Runtime.getRuntime()::halt)
    }
    // we're exiting the process now, no need for the shutdown hook, unless the JVM is already shutting down
    try {
      Runtime.getRuntime().removeShutdownHook(shutdownHook)
    } catch (_: IllegalStateException) {
      // the hook stopped the application, and the JVM exits once it returns
    }
    exitCode
  } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    log.fatal(e) { "Failed to start application. Exiting..." }
    -1
  }
  exitProcess(exitCode)
}
