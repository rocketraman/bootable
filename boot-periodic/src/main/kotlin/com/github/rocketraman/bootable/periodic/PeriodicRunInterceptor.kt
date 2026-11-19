package com.github.rocketraman.bootable.periodic

/**
 * Runs code around each run of a [PeriodicAppService], such as a span that the run is in: `boot-periodic-otel`
 * traces each run this way. An interceptor must call [run] once, and let what it throws propagate.
 *
 * Interceptors wrap the run in order, so the first is outermost. A run the gate skips isn't intercepted.
 */
interface PeriodicRunInterceptor {
  /** Runs [run], a run of the periodic service named [service], with this interceptor's code around it. */
  suspend fun intercept(service: String, run: suspend () -> Unit)
}

/**
 * What a [PeriodicAppService] does with a run that failed, after which the loop carries on, unless
 * [PeriodicConfig.maxConsecutiveFailures] runs in a row have failed. The default, [Log], logs it as an error.
 */
fun interface PeriodicFailureHandler {
  /**
   * Handles [failure], what the run of the periodic service named [service] threw, which is the [consecutiveFailures]th
   * run in a row to fail. It runs on the loop's coroutine, so it must return promptly; what it throws is logged.
   */
  fun onFailure(service: String, failure: Throwable, consecutiveFailures: Int)

  companion object {
    /** Logs the failure as an error. */
    val Log: PeriodicFailureHandler = PeriodicFailureHandler { service, failure, consecutiveFailures ->
      log.error(failure) {
        "Periodic run of $service failed" + if (consecutiveFailures > 1) " ($consecutiveFailures in a row)" else ""
      }
    }
  }
}
