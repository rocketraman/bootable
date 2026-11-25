package com.github.rocketraman.bootable.boot

import org.apache.logging.log4j.kotlin.logger

/**
 * When a coroutine in a service's scope fails, the [ScopeFailurePolicy] set via [AppService.scopeFailurePolicy]
 * drives the result. By default, it is [StopApplication], since a service failure that is not explicitly
 * handled by the developer puts the system in an unknown state, and in this situation it is better to fail-fast
 * than to remain partially running. Modern cloud systems automatically restart failed pods -- compromised ones
 * keep on going. But users can handle failures explicitly, and request other behavior.
 */
sealed class ScopeFailurePolicy {
  /** Whether the service's scope supervises its coroutines: if so, one that fails doesn't cancel the others. */
  internal abstract val supervise: Boolean

  /** Reacts to [failure], from [coroutine] in [service]'s scope, of [bootable]. */
  internal abstract fun onFailure(bootable: Bootable, service: AppService, coroutine: String?, failure: Throwable)

  /**
   * The failure cancels the service's other coroutines, marks the service [ServiceState.Failed], and stops the
   * application, in order, with [exitCode]. If the application is already stopping, its exit code doesn't change.
   */
  data class StopApplication(val exitCode: Int = 1) : ScopeFailurePolicy() {
    override val supervise get() = false

    override fun onFailure(bootable: Bootable, service: AppService, coroutine: String?, failure: Throwable) {
      log.error(failure) { "==> Service ${service.name} failed… stopping the application." }
      bootable.serviceFailed(service)
      bootable.stop(exitCode)
    }
  }

  /**
   * The service's scope supervises its coroutines: a coroutine that fails is logged, and only it ends, while the
   * service's other coroutines, the service, and the application carry on. Such a service can't stop the application
   * by failing its scope.
   */
  data object LogAndContinue : ScopeFailurePolicy() {
    override val supervise get() = true

    override fun onFailure(bootable: Bootable, service: AppService, coroutine: String?, failure: Throwable) {
      log.error(failure) {
        "==> Coroutine ${coroutine ?: "(unnamed)"} of service ${service.name} failed… the service carries on."
      }
    }
  }

  private companion object {
    // the same logger as Bootable's own lifecycle messages
    val log = logger(Bootable::class.java.name)
  }
}
