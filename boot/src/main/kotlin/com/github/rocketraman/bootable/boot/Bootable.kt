package com.github.rocketraman.bootable.boot

import org.apache.logging.log4j.kotlin.logger
import com.github.rocketraman.bootable.common.Ignore
import com.github.rocketraman.bootable.common.safeExec
import com.github.rocketraman.bootable.common.threadStacks
import java.util.concurrent.SynchronousQueue
import kotlin.concurrent.timer
import kotlin.system.exitProcess
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class Bootable(
  private val appServices: Set<AppService>,
  private val lifecycleControllers: Set<LifecycleController>,
  private val maxShutdownTime: Long = 30
) {

  @Volatile private var running = true
  private val terminationQueue = SynchronousQueue<Int>()
  private val shutdownHook = Thread { appTerminate() }
  private val disabledServices = mutableSetOf<String>()

  companion object {
    private val log = logger()
  }

  /**
   * This is internal as it is intended to be used only from the [com.github.rocketraman.bootable.boot] helper function.
   */
  internal fun boot(startupMark: TimeMark = TimeSource.Monotonic.markNow()) {
    log.info("=> Initializing (${startupMark.elapsedNow()})…")
    appInit()

    log.info("=> Starting (${startupMark.elapsedNow()})…")
    appStart()

    // shouldn't need this if a signal handler is installed, but install this shutdown hook as a fallback
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    log.info("=> Running (${startupMark.elapsedNow()})")
    val runningMark = TimeSource.Monotonic.markNow()
    val exitCode = awaitTermination()

    log.info("=> Shutting down (${startupMark.elapsedNow()})")

    val shutdownMark = TimeSource.Monotonic.markNow()
    safeExec { appShutdown() }
    safeExec { appCleanup() }

    log.debug { "Shut down application in ${shutdownMark.elapsedNow()}, total uptime was ${runningMark.elapsedNow()}." }
    log.info("=> Stopped")

    exit(exitCode)
  }

  private fun appInit() {
    lifecycleControllers.sortedByDescending { it.priority() }.forEach {
      it.appInit()
    }
  }

  private fun appStart() {
    lifecycleControllers.sortedByDescending { it.priority() }.forEach {
      if(running) {
        it.appStart(ServiceContext(appServices, ::serviceStart, ::serviceShutdown), ::appTerminate)
      }
    }
  }

  private fun awaitTermination(): Int {
    while(running) {
      try { return terminationQueue.take() } catch (e: InterruptedException) { Ignore }
    }
    return 0
  }

  private fun appShutdown() {
    lifecycleControllers.sortedBy { it.priority() }.forEach {
      it.appShutdown(ServiceContext(appServices, ::serviceStart, ::serviceShutdown), maxShutdownTime, ::halt)
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
      synchronized(disabledServices) {
        disabledServices.remove(appService.name())
      }
    } else {
      log.warn { "==> Skipping start of ${appService.name()}, these controllers voted NO: ${votedNo.map { it.name() }}…" }
      synchronized(disabledServices) {
        disabledServices.add(appService.name())
      }
    }
  }

  private fun serviceShutdown(appService: AppService) {
    val isDisabled = synchronized(disabledServices) {
      disabledServices.contains(appService.name())
    }
    if(isDisabled) {
      log.info { "==> Stopping ${appService.name()}… disabled, ignoring shutdown" }
    } else {
      log.info { "==> Stopping ${appService.name()}…" }
      safeExec({ e -> log.warn(e) { "Error stopping ${appService.name()}, ignoring…" } }) {
        appService.shutdown()
        synchronized(disabledServices) {
          disabledServices.add(appService.name())
        }
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
