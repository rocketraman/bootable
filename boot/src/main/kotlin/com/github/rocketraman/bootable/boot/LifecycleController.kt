package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.common.Ignore

data class ServiceContext(
  val appServices: Set<AppService>,
  val serviceStart: (appService: AppService) -> Unit,
  val serviceShutdown: (appService: AppService) -> Unit
)

enum class ServiceStartVote {
  YES,
  NO
}

/**
 * An interface for controllers for application and [AppService] lifecycles. For example, such a controller may
 * offer the capability to stop or start services at application start and shutdown, on command, or on demand.
 */
interface LifecycleController {
  /**
   * The controller name, which defaults to the simple java class name.
   */
  fun name(): String = javaClass.simpleName

  /**
   * Executed before application start. Can be used by Lifecycle controllers to initialize state necessary to
   * make subsequent decisions.
   */
  fun appInit() = Ignore

  /**
   * Executed when the application is started.
   * @param normalStop A callback to execute a normal stop on the application. This would generally be saved by
   *   an implementation for later use.
   */
  fun appStart(serviceContext: ServiceContext, normalStop: (exitCode: Int) -> Unit) { Ignore }

  /**
   * When a service is started, lifecycle controllers may vote on whether it is allowed to start or not. This may
   * be used, for example, for persistently disabling a given service via a controller. Note that, depending on
   * the priority of a controller, a controller may be asked to vote on the start of a service *before* [appStart]
   * is called on that controller. This is because a higher priority controller may be attempting to start a service
   * during *it's* [appStart] call.
   */
  fun serviceStartVote(appService: AppService): ServiceStartVote = ServiceStartVote.YES

  /**
   * Executed when the application is shutdown.
   * @param exitProcess A callback to force exit the process with a given return code.
   */
  fun appShutdown(serviceContext: ServiceContext, maxShutdownTime: Long, exitProcess: (exitCode: Int) -> Unit) { Ignore }

  /**
   * Executed after application shutdown for final cleanup.
   */
  fun appCleanup() = Ignore

  /**
   * The startup/shutdown priority of the controller. Controllers with higher priority are started first, and shut down
   * last. Controllers  default to a priority of 0, which means by default startup and shutdown order are undefined.
   * Generally controllers that operate at app startup and shutdown should have a high priority, and controllers that
   * operate at runtime should have a lower priority.
   */
  fun priority() = 0
}
