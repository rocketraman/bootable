package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.common.Ignore
import kotlinx.coroutines.CoroutineScope

/**
 * An application service, with a lifecycle.
 *
 * Passing implementations of AppService to [Bootable] (directly, or via a DI multibinding such as Metro's `@IntoSet`)
 * registers them with the boot system, which starts them up and shuts them down in the order of
 * their [ordering].
 *
 * Its start and [shutdown] suspend, and run on the lifecycle's own thread, one service at a time. Wrap blocking calls in
 * them, such as JDBC or file IO, in `withContext(Dispatchers.IO)`.
 *
 * ## Cancellation
 *
 * Bootable stops a service's work by cancelling it: a stop cancels a start still running, the shutdown timeout
 * cancels a shutdown, and once the service has stopped, Bootable cancels its scope. Cancellation is a
 * `CancellationException`, thrown where the coroutine suspends, so code that catches `Exception` or `Throwable`
 * catches it too, and, if it carries on, keeps the coroutine running past its cancellation: a shutdown that
 * outlasts its timeout, or a loop that never ends. `runCatching` catches it the same way. Rethrow it if the coroutine
 * is cancelled, and treat any other, such as a `withTimeout` inside the `try` timing out, as the failure it is:
 *
 * ```
 * try {
 *   client.release()
 * } catch (e: Exception) {
 *   if (e is CancellationException) currentCoroutineContext().ensureActive()
 *   log.warn(e) { "Couldn't release" }
 * }
 * ```
 *
 * Rethrowing every `CancellationException` instead would let a timeout inside the `try` end the coroutine silently,
 * as if it had been cancelled.
 */
interface AppService {
  /**
   * How this service's scope handles the failure of one of its coroutines: whether the scope supervises them, and what
   * a failure does. By default, [ScopeFailurePolicy.StopApplication]: the failure cancels the service's other
   * coroutines, marks the service [ServiceState.Failed], and stops the application, in order, with exit code 1.
   *
   * A service whose coroutines are independent of each other, such as one that launches fire-and-forget work from
   * request handlers, overrides it with [ScopeFailurePolicy.LogAndContinue], so that its scope supervises them.
   */
  val scopeFailurePolicy: ScopeFailurePolicy get() = ScopeFailurePolicy.StopApplication()

  /**
   * The service name, which defaults to the simple java class name.
   */
  fun name(): String = javaClass.simpleName

  /**
   * Starts the service, and returns once it has started, for a service with no background work. A stop asked for while
   * the application starts cancels the start, at its next suspension point.
   *
   * Don't launch background work, work that outlives the start, from here: override `start(scope)` instead, and launch
   * it in that scope. In particular, don't launch it in a scope made from this function's own coroutine context, such
   * as `CoroutineScope(currentCoroutineContext())`: the work becomes a child of the application's start, which then
   * doesn't complete until the work does, so the application never gets past starting. Nor launch it in `GlobalScope`,
   * or in a scope the service makes itself: nothing stops that work when the application stops, and its failure
   * doesn't stop the application.
   *
   * Concurrent work that is part of the start, such as loading two caches at once, is fine in `coroutineScope { }`,
   * which returns once that work is done.
   */
  suspend fun start() { Ignore }

  /**
   * Starts the service, and returns once it has started, for a service that runs work in the background: launch that
   * work in [scope]. Bootable calls this one, which, unless it is overridden, calls [start].
   *
   * Override one `start` or the other: a service that overrides both has only this one called, unless it calls
   * [start] itself.
   *
   * [scope] is the service's own, a child of the application's, on `Dispatchers.Default`. It lives until the service
   * has stopped: after [shutdown] returns, Bootable cancels it and waits for its coroutines, so the service needn't
   * track, cancel or join them itself. A service that wants its work to finish gracefully, rather than be cancelled,
   * waits for it in [shutdown].
   *
   * If a coroutine in [scope] fails, the service has failed: its other coroutines are cancelled, and the application
   * stops, in order, with exit code 1, unless its [scopeFailurePolicy] says otherwise. Other services' work isn't
   * cancelled until they stop. A service that notices outside [scope], such as in a request handler, that it can't go
   * on, fails it with `scope.launch { throw e }`.
   */
  suspend fun start(scope: CoroutineScope) = start()

  /**
   * Stops the service. Called only if its start returned: a service whose start fails partway, and must release
   * what it acquired, cleans up in its start itself before it throws.
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
