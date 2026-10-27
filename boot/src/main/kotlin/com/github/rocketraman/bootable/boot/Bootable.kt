package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.common.Ignore
import com.github.rocketraman.bootable.common.safeExec
import com.github.rocketraman.bootable.common.threadStacks
import com.github.rocketraman.bootable.logging.log4j2.LoggingType
import com.github.rocketraman.bootable.logging.log4j2.loggingInit
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.SynchronousQueue
import kotlin.concurrent.timer
import kotlin.system.exitProcess
import kotlin.time.TimeMark
import kotlin.time.TimeSource
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
 * fails, the services that had started are shut down, in reverse, before the process exits.
 *
 * [maxShutdownTime] is a [boot] argument rather than a constructor dependency, so the constructor takes only what
 * makes up the application.
 */
class Bootable(
  private val appServices: Set<AppService>,
  private val lifecycleControllers: Set<LifecycleController> = defaultLifecycleControllers(),
  val startOrder: StartOrder = DefaultStartOrder,
  val tiebreaker: StartOrder.Tiebreaker = StartOrder.Tiebreaker.ByName,
) {

  @Volatile private var running = true
  private val terminationQueue = SynchronousQueue<Int>()
  private val shutdownHook = Thread { appTerminate() }
  /** The services started, and not yet stopped. */
  private val startedServices: MutableSet<AppService> =
    Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))
  private lateinit var serviceContext: ServiceContext

  companion object {
    private val log = logger()
  }

  /**
   * This is internal as it is intended to be used only from the [com.github.rocketraman.bootable.boot] helper function.
   */
  internal fun boot(startupMark: TimeMark = TimeSource.Monotonic.markNow(), maxShutdownTime: Long = 30) {
    exit(run(startupMark, maxShutdownTime))
  }

  /** Runs the application until it terminates, and returns the exit code. */
  internal fun run(startupMark: TimeMark = TimeSource.Monotonic.markNow(), maxShutdownTime: Long = 30): Int {
    appPlan()

    log.info("=> Initializing (${startupMark.elapsedNow()})…")
    appInit()

    log.info("=> Starting (${startupMark.elapsedNow()})…")
    @Suppress("TooGenericExceptionCaught")
    try {
      appStart()
    } catch (e: Exception) {
      log.fatal(e) { "Failed to start application, stopping the services that started…" }
      running = false
      appStop(startupMark, maxShutdownTime)
      return -1
    }

    // shouldn't need this if a signal handler is installed, but install this shutdown hook as a fallback
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    log.info("=> Running (${startupMark.elapsedNow()})")
    val runningMark = TimeSource.Monotonic.markNow()
    val exitCode = awaitTermination()

    appStop(startupMark, maxShutdownTime)
    log.debug { "Total uptime was ${runningMark.elapsedNow()}." }
    return exitCode
  }

  private fun appStop(startupMark: TimeMark, maxShutdownTime: Long) {
    log.info("=> Shutting down (${startupMark.elapsedNow()})")

    val shutdownMark = TimeSource.Monotonic.markNow()
    safeExec { appShutdown(maxShutdownTime) }
    safeExec { appCleanup() }

    log.debug { "Shut down application in ${shutdownMark.elapsedNow()}." }
    log.info("=> Stopped")
  }

  private fun appPlan() {
    val plan = phasedStartPlan(appServices, startOrder, tiebreaker)
    val shuffled =
      (tiebreaker as? StartOrder.Tiebreaker.Shuffled)?.let { " (ties shuffled, seed ${it.seed})" }.orEmpty()
    log.info { "Start plan$shuffled: ${plan.describe()}" }
    serviceContext = ServiceContext(appServices, plan.flatMap { it.services }, ::serviceStart, ::serviceShutdown)
  }

  private fun appInit() {
    lifecycleControllers.sortedByDescending { it.priority() }.forEach {
      it.appInit()
    }
  }

  private fun appStart() {
    lifecycleControllers.sortedByDescending { it.priority() }.forEach {
      if(running) {
        it.appStart(serviceContext, ::appTerminate)
      }
    }
  }

  private fun awaitTermination(): Int {
    while(running) {
      try { return terminationQueue.take() } catch (e: InterruptedException) { Ignore }
    }
    return 0
  }

  private fun appShutdown(maxShutdownTime: Long) {
    lifecycleControllers.sortedBy { it.priority() }.forEach {
      it.appShutdown(serviceContext, maxShutdownTime, ::halt)
    }
  }

  private fun appCleanup() {
    lifecycleControllers.sortedBy { it.priority() }.forEach {
      it.appCleanup()
    }
  }

  private fun serviceStart(appService: AppService) {
    val votedNo = lifecycleControllers.filter { it.serviceStartVote(appService) == ServiceStartVote.NO }
    if (votedNo.isEmpty()) {
      log.info { "==> Starting ${appService.name()}…" }
      if(appService is AdvancedAppService) {
        appService.start { die(appService) }
      } else {
        appService.start()
      }
      startedServices += appService
    } else {
      log.warn { "==> Skipping start of ${appService.name()}, these controllers voted NO: ${votedNo.map { it.name() }}…" }
    }
  }

  private fun serviceShutdown(appService: AppService) {
    if(!startedServices.remove(appService)) {
      log.info { "==> Stopping ${appService.name()}… not started, ignoring shutdown" }
    } else {
      log.info { "==> Stopping ${appService.name()}…" }
      safeExec({ e -> log.warn(e) { "Error stopping ${appService.name()}, ignoring…" } }) {
        appService.shutdown()
      }
    }
  }

  private fun appTerminate(exitCode: Int = 0) {
    if(running) {
      running = false
      terminationQueue.put(exitCode)
    }
  }

  private fun die(service: AppService) {
    log.warn { "==> Service ${service.javaClass.simpleName} died… shutting down system." }
    timer(name = "forceShutdownTimer", daemon = true, initialDelay = 30000, period = Long.MAX_VALUE) {
      log.warn { "Application died but did not shutdown normally, forcing exit. Current stack trace: ${threadStacks()}" }
      halt(1)
    }
    appTerminate(1)
  }

  private fun exit(status: Int = 0) {
    // we're exiting the process now, no need for the shutdown hook
    Runtime.getRuntime().removeShutdownHook(shutdownHook)
    exitProcess(status)
  }

  private fun halt(status: Int = 0) {
    Runtime.getRuntime().halt(status)
  }
}

/**
 * Bootable's default lifecycle controllers: [AppStartStopLifecycleController] (start services on boot, stop them
 * on shutdown) and [StopSignalHandlerLifecycleController] (clean shutdown on HUP/INT/TERM).
 *
 * Pass a different set to the [Bootable] you build in [boot] to run a custom lifecycle — for example
 * starting/stopping services on command from an RPC or web request rather than at boot.
 */
fun defaultLifecycleControllers(): Set<LifecycleController> = setOf(
  AppStartStopLifecycleController(),
  StopSignalHandlerLifecycleController(),
)

/**
 * The convenience entry point: initialize logging, then build and boot a [Bootable].
 *
 * Bootable embeds no dependency-injection framework — you assemble the application inside the lambda however you
 * like (plain Kotlin, or Kodein/Metro/etc. if you prefer), and return the [Bootable] to run. The lambda runs
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
 */
fun boot(
  maxShutdownTime: Long = 30,
  loggingType: LoggingType? = null,
  bootable: () -> Bootable,
) {
  val startupMark = TimeSource.Monotonic.markNow()

  loggingInit(loggingType = loggingType)
  val log = logger("Bootable")

  @Suppress("TooGenericExceptionCaught")
  try {
    log.info("=> Creating application")
    bootable().boot(startupMark, maxShutdownTime)
  } catch (e: Exception) {
    log.fatal(e) { "Failed to start application. Exiting..." }
    exitProcess(-1)
  }
}
