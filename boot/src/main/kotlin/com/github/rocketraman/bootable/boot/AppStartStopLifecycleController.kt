package com.github.rocketraman.bootable.boot

import kotlin.time.Duration
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.logging.log4j.kotlin.logger

/**
 * This lifecycle controller starts all AppServices at application start time, in the order of the
 * [start plan][ServiceContext.startPlan], and stops them in the reverse order at application stop time.
 *
 * The stop is bounded by the shutdown timeout: at `maxShutdownTime`, the service stopping is cancelled, and the
 * services not yet stopped are skipped. [Bootable] then logs each service's state and the stacks, and exits with exit
 * code 1; if a service blocks in code that ignores cancellation, its watchdog halts the process shortly after.
 */
class AppStartStopLifecycleController: LifecycleController {
  val log = logger()

  override suspend fun appStart(serviceContext: ServiceContext, normalStop: (exitCode: Int) -> Unit) {
    serviceContext.startPlan.forEach { serviceContext.serviceStart(it) }
  }

  override suspend fun appShutdown(serviceContext: ServiceContext, maxShutdownTime: Duration) {
    withTimeoutOrNull(maxShutdownTime) {
      serviceContext.startPlan.asReversed().forEach { serviceContext.serviceShutdown(it) }
    } ?: log.warn { "Services didn't stop within $maxShutdownTime: cancelled the stop, and skipped the rest" }
  }

  override fun priority(): Int = Int.MAX_VALUE
}
