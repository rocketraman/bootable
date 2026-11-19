package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.common.Ignore

/**
 * An application service, with a lifecycle.
 *
 * Passing implementations of AppService to [Bootable] (directly, or via a DI multibinding such as Kodein's
 * `bindAppService`) registers them with the boot system, which starts them up and shuts them down in the order of
 * their [ordering].
 *
 * [start] and [shutdown] suspend, and run on the lifecycle's own thread, one service at a time. Wrap blocking calls in
 * them, such as JDBC or file IO, in `withContext(Dispatchers.IO)`.
 */
interface AppService {
  /**
   * The service name, which defaults to the simple java class name.
   */
  fun name(): String = javaClass.simpleName

  /**
   * Starts the service, and returns once it has started. A stop asked for while the application starts cancels the
   * start, at its next suspension point.
   *
   * A service that runs work in the background, beyond its start, implements [AdvancedAppService] instead, and launches
   * that work in the scope it is given: [start] has no scope that outlives it, and shouldn't make one.
   */
  suspend fun start() { Ignore }

  /**
   * Stops the service. Called only if [start] returned: a service whose start fails partway, and must release
   * what it acquired, cleans up in [start] itself before it throws.
   *
   * The stop is bounded by the application's shutdown timeout, at which it is cancelled.
   */
  suspend fun shutdown() { Ignore }

  /**
   * Where this service starts relative to the others: its [Phase], and the [Milestone]s it provides and waits
   * for. Services start phase by phase, and stop in the reverse order. The default is the [StartOrder]'s default
   * phase, [DefaultPhase.Application] unless the app changes it, with no milestones, which suits most of an
   * application's own services.
   *
   * Services that their orderings leave unordered may start in any order (see [StartOrder.Tiebreaker]): if the
   * order between them matters, declare a milestone.
   */
  fun ordering(): Ordering = Ordering()
}
