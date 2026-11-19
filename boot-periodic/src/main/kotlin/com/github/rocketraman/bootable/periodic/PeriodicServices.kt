package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Ordering
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock

/**
 * What the application's [PeriodicAppService]s share: their default [gate], [clock], [interceptors], and failure
 * handler, so that each loop needs only its name, config, and work. The DI flavors bind one, from the application's
 * `LeaderElection`, `Clock`, and contributed interceptors:
 *
 * ```
 * inBindSet<AppService> {
 *   add { singleton { instance<PeriodicServices>().create("notifications", instance<NotificationsConfig>().loop) { instance<Notifications>().sendPending() } } }
 * }
 * ```
 */
class PeriodicServices(
  /** The gate of each service that doesn't pass its own, such as [PeriodicGate.leader]. */
  val gate: PeriodicGate = PeriodicGate.Always,
  val clock: Clock = Clock.System,
  val interceptors: List<PeriodicRunInterceptor> = emptyList(),
  val onFailure: PeriodicFailureHandler = PeriodicFailureHandler.Log,
) {
  /** A [PeriodicAppService] named [name] that runs [work] as [config] says, with these shared settings. */
  fun create(
    name: String,
    config: PeriodicConfig,
    gate: PeriodicGate = this.gate,
    ordering: Ordering = Ordering(DefaultPhase.Serving),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    work: suspend () -> Unit,
  ): PeriodicAppService = PeriodicAppService(name, config, gate, clock, interceptors, onFailure, ordering, dispatcher, work)
}
