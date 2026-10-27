package com.github.rocketraman.bootable.boot

import org.apache.logging.log4j.kotlin.logger
import com.github.rocketraman.bootable.common.threadStacks
import kotlin.concurrent.timer

/**
 * This lifecycle controller starts all AppServices at application start time, in the order of the
 * [start plan][ServiceContext.startPlan], and stops them in the reverse order at application stop time. It also
 * forces shutdown via a timer -- if a normal shutdown has not occurred in the timeout period, the controller logs
 * the current thread stacks (which may be useful for debugging why the shutdown did not complete normally), and
 * then exits with a non-zero exit code.
 */
class AppStartStopLifecycleController: LifecycleController {
  val log = logger()

  override fun appStart(serviceContext: ServiceContext, normalStop: (exitCode: Int) -> Unit) {
    serviceContext.startPlan.forEach(serviceContext.serviceStart)
  }

  override fun appShutdown(serviceContext: ServiceContext, maxShutdownTime: Long, haltProcess: (status: Int) -> Unit) {
    timer(name = "shutdownTimer", daemon = true, initialDelay = maxShutdownTime * 1000, period = Long.MAX_VALUE) {
      log.warn { "Application stopped but did not shutdown normally, forcing exit. Current stack trace: ${threadStacks()}" }
      haltProcess(1)
    }
    serviceContext.startPlan.asReversed().forEach(serviceContext.serviceShutdown)
  }

  override fun priority(): Int = Int.MAX_VALUE
}
