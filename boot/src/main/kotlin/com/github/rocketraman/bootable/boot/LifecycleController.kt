package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.common.Ignore
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The application's services, for a [LifecycleController] to start and stop.
 *
 * @property appServices every service the application was given.
 * @property startPlan the same services, in the order they start in; they stop in the reverse order. See [startPlan].
 * @property serviceStart starts a service, unless a controller votes against it (see
 *   [LifecycleController.serviceStartVote]).
 * @property serviceShutdown stops a service, unless it was never started.
 * @property states each service's [ServiceState], in the order of the [startPlan].
 */
data class ServiceContext(
  val appServices: Set<AppService>,
  val startPlan: List<AppService>,
  val serviceStart: suspend (appService: AppService) -> Unit,
  val serviceShutdown: suspend (appService: AppService) -> Unit,
  val states: StateFlow<Map<AppService, ServiceState>> = MutableStateFlow(emptyMap()),
)

enum class ServiceStartVote {
  YES,
  NO
}

/** Where a service is in its lifecycle, as [Bootable] tracks it, in [ServiceContext.states]. */
enum class ServiceState {
  /** Not started yet, or its start was cancelled by a stop. */
  Pending,
  Starting,
  Running,
  /** A controller voted against starting it (see [LifecycleController.serviceStartVote]). */
  Skipped,
  /** Its start threw, or a coroutine in its scope failed (see `AppService.start(scope)`). */
  Failed,
  Stopping,
  Stopped,
}

/**
 * An interface for controllers for application and [AppService] lifecycles. For example, such a controller may
 * offer the capability to stop or start services at application start and shutdown, on command, or on demand.
 *
 * The hooks that may do IO suspend, and run on the lifecycle's own thread, one at a time.
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
   * @param normalStop A callback to execute a normal stop on the application, with an exit code. It never blocks,
   *   can be called from any thread, at any time, and only its first call, or that of any other way to stop, counts.
   *   This would generally be saved by an implementation for later use.
   */
  suspend fun appStart(serviceContext: ServiceContext, normalStop: (exitCode: Int) -> Unit) { Ignore }

  /**
   * Runs [start], the application's start, with this controller's code around it, such as a span that the whole start
   * runs in. [start] calls every controller's [appStart], so it starts the services, and returns once the application
   * is running, or throws if a service failed to start, or if a stop cancelled the start. An implementation must call
   * [start] once, and let what it throws propagate.
   *
   * [timeline] is the application's boot so far: when it began, with [boot] before logging was initialized and the
   * [Bootable] built, and the stages since, such as building the application, which includes decoding its config.
   *
   * Each controller wraps the start of the controllers of lower [priority], so the highest priority's code is
   * outermost. The default runs [start] alone.
   */
  suspend fun aroundAppStart(serviceContext: ServiceContext, timeline: StartupTimeline, start: suspend () -> Unit) =
    start()

  /**
   * Executed once the application's whole start has completed, including every controller's [aroundAppStart], so
   * the application is running. It is called in order of [priority], highest first, and not at all if the start
   * failed, or the application began to stop during it. A controller that throws fails the start, as a service that
   * fails to start does, and the controllers after it aren't called.
   */
  suspend fun appRunning() { Ignore }

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
   * was requested. An implementation must call [start] once, and let what it throws propagate.
   *
   * [start] may resume on another thread than it began on, so a thread-local context, such as OpenTelemetry's, is
   * carried as a coroutine context element, rather than set on the calling thread.
   *
   * Each controller wraps the start of the controllers of lower [priority], so the highest priority's code is
   * outermost. The default runs [start] alone.
   */
  suspend fun aroundServiceStart(appService: AppService, start: suspend () -> Unit) = start()

  /**
   * Executed when the application is shutdown. A controller that stops services bounds their stop by
   * [maxShutdownTime], as [AppStartStopLifecycleController] does.
   */
  suspend fun appShutdown(serviceContext: ServiceContext, maxShutdownTime: Duration) { Ignore }

  /**
   * Executed after application shutdown for final cleanup.
   */
  suspend fun appCleanup() { Ignore }

  /**
   * The startup/shutdown priority of the controller. Controllers with higher priority are started first, and shut down
   * last. Controllers  default to a priority of 0, which means by default startup and shutdown order are undefined.
   * Generally controllers that operate at app startup and shutdown should have a high priority, and controllers that
   * operate at runtime should have a lower priority.
   */
  fun priority() = 0
}
