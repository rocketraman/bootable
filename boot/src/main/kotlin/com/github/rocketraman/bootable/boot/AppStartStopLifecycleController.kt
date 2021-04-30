package com.github.rocketraman.bootable.boot

import org.apache.logging.log4j.kotlin.logger
import com.github.rocketraman.bootable.common.threadStacks
import kotlin.concurrent.timer

/**
 * This lifecycle controller starts all AppServices at application start time, and stops them at application
 * stop time. It also forces shutdown via a timer -- if a normal shutdown has not occurred in the timeout
 * period, the controller logs the current thread stacks (which may be useful for debugging why the shutdown
 * did not complete normally), and then exits with a non-zero exit code.
 */
class AppStartStopLifecycleController: LifecycleController {
  val log = logger()

  override fun appStart(serviceContext: ServiceContext, normalStop: (exitCode: Int) -> Unit) {
    serviceContext.appServices.sortedByDescending { it.priority() }.forEach(serviceContext.serviceStart)
  }

  override fun appShutdown(serviceContext: ServiceContext, maxShutdownTime: Long, exitProcess: (exitCode: Int) -> Unit) {
    timer(name = "shutdownTimer", daemon = true, initialDelay = maxShutdownTime * 1000, period = Long.MAX_VALUE) {
      log.warn { "Application stopped but did not shutdown normally, forcing exit. Current stack trace: ${threadStacks()}" }
      exitProcess(1)
    }
    serviceContext.appServices.sortedBy { it.priority() }.forEach(serviceContext.serviceShutdown)
  }

  override fun priority(): Int = Int.MAX_VALUE
}
