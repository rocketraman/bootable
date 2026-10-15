package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.logging.log4j2.LoggingType
import com.github.rocketraman.bootable.logging.log4j2.loggingInit
import org.apache.logging.log4j.kotlin.logger
import kotlin.system.exitProcess
import kotlin.time.TimeSource

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
