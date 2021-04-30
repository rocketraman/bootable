package com.github.rocketraman.bootable.boot

import org.apache.logging.log4j.kotlin.logger
import com.github.rocketraman.bootable.common.safeExec
import sun.misc.Signal
import sun.misc.SignalHandler

/**
 * This lifecycle controller handles HUP, INT, and TERM signals to the JVM, and stops the application if
 * one of these is received. The signal handler is installed after the app is started, so as to prevent
 * race conditions if an app is terminated while it is still initializing.
 */
class StopSignalHandlerLifecycleController: LifecycleController {
  val log = logger()

  override fun appStart(serviceContext: ServiceContext, normalStop: (exitCode: Int) -> Unit) {
    StopSignalHandler(normalStop)
    log.debug("Stop signal handler installed")
  }

  /**
   * Gracefully handle SIGHUP, SIGINT, or SIGTERM. This is cleaner than executing a shutdown hook because it will
   * run *before* other shutdown hooks installed in the system. The exit code in these cases is normally 128+signum
   * but we'll consider this a normal exit and have an exit code of 0.
   */
  private class StopSignalHandler private constructor(val normalStop: (exitCode: Int) -> Unit): SignalHandler {
    companion object {
      const val HUP = "HUP"
      const val INT = "INT"
      const val TERM = "TERM"

      val log = logger()

      /**
       * Create a signal handler, registering itself on `HUP`, `INT`, and `TERM`. On Windows, signals like `HUP`
       * cause the JVM to throw IllegalArgumentException "Unknown Signal". Ignore these errors.
       */
      operator fun invoke(normalStop: (exitCode: Int) -> Unit): StopSignalHandler {
        return StopSignalHandler(normalStop).apply {
          safeExec { Signal.handle(Signal(HUP), this) }
          safeExec { Signal.handle(Signal(INT), this) }
          safeExec { Signal.handle(Signal(TERM), this) }
        }
      }
    }

    override fun handle(signal: Signal) = safeExec({ t -> log.error(t) { "Signal handler failed, signal=$signal" } }) {
      log.debug { "Handling signal $signal" }
      when(signal.name) {
        HUP, INT, TERM -> normalStop(0)
      }
    }
  }
}
