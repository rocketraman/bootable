package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.common.Ignore

/**
 * The application's services, for a [LifecycleController] to start and stop.
 *
 * @property appServices every service the application was given.
 * @property startPlan the same services, in the order they start in; they stop in the reverse order. See [startPlan].
 * @property serviceStart starts a service, unless a controller votes against it (see
 *   [LifecycleController.serviceStartVote]).
 * @property serviceShutdown stops a service, unless it was never started.
 */
data class ServiceContext(
  val appServices: Set<AppService>,
  val startPlan: List<AppService>,
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
   * Runs [start], the application's start, with this controller's code around it, such as a span that the whole start
   * runs in. [start] calls every controller's [appStart], so it starts the services, and returns once the application
   * is running, or throws if a service failed to start. An implementation must call [start] once, on the calling
   * thread, and let what it throws propagate.
   *
   * [timeline] is the application's boot so far: when it began, with [boot] before logging was initialized and the
   * [Bootable] built, and the stages since, such as building the application, which includes decoding its config.
   *
   * Each controller wraps the start of the controllers of lower [priority], so the highest priority's code is
   * outermost. The default runs [start] alone.
   */
  fun aroundAppStart(serviceContext: ServiceContext, timeline: StartupTimeline, start: () -> Unit) = start()

  /**
   * Executed once the application's whole start has completed, including every controller's [aroundAppStart], so
   * the application is running. It is called in order of [priority], highest first, and not at all if the start
   * failed, or the application began to stop during it. A controller that throws fails the start, as a service that
   * fails to start does, and the controllers after it aren't called.
   */
  fun appRunning() = Ignore

  /**
   * When a service is started, lifecycle controllers may vote on whether it is allowed to start or not. This may
   * be used, for example, for persistently disabling a given service via a controller. Note that, depending on
   * the priority of a controller, a controller may be asked to vote on the start of a service *before* [appStart]
   * is called on that controller. This is because a higher priority controller may be attempting to start a service
   * during *it's* [appStart] call.
   */
  fun serviceStartVote(appService: AppService): ServiceStartVote = ServiceStartVote.YES

  /**
   * Runs [start], which starts [appService], with this controller's code around it, such as a span that the service's
   * start runs in. It is called for each service that is allowed to start (see [serviceStartVote]), however the start
   * was requested. An implementation must call [start] once, on the calling thread, and let what it throws propagate.
   *
   * Each controller wraps the start of the controllers of lower [priority], so the highest priority's code is
   * outermost. The default runs [start] alone.
   */
  fun aroundServiceStart(appService: AppService, start: () -> Unit) = start()

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
