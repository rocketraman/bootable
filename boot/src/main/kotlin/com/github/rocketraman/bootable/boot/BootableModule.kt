package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.logging.log4j2.loggingInit
import org.apache.logging.log4j.kotlin.logger
import org.kodein.di.*
import kotlin.system.exitProcess

/**
 * A convenience function that initializes logging, creates a Kodein context, imports all available
 * app services and app service lifecycle controllers (including the built-in application start/stop
 * controller and signal handler controllers), obtains additional modules to boot using the provided
 * argument, and then calls [Bootable.boot].
 */
fun boot(maxShutdownTime: Long = 30, mainBuilder: DI.MainBuilder.() -> Unit) {
  loggingInit()
  val log = logger("Bootable")

  try {
    log.info("=> Creating application dependencies")

    @Suppress("RemoveExplicitTypeArguments")
    DI.direct {
      bindAppServiceSet()

      bindLifecycleControllerSet()
      bindLifecycleController() with singleton { AppStartStopLifecycleController() }
      bindLifecycleController() with singleton { StopSignalHandlerLifecycleController() }

      bind { singleton { Bootable(
        instance<Set<AppService>>(),
        instance<Set<LifecycleController>>(),
        maxShutdownTime
      ) } }
      mainBuilder()
    }.instance<Bootable>().boot()
  } catch (e: Exception) {
    log.fatal(e) { "Failed to start application. Exiting..." }
    exitProcess(-1)
  }
}
