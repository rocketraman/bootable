@file:JvmName("PeriodicModuleInara")

package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.leader.LeaderElectionModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryApiModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.inara.DISet
import kotlin.time.Clock

/** Marks a module member as one of the [PeriodicRunInterceptor]s of the application's periodic services. */
@DISet(of = PeriodicRunInterceptor::class)
annotation class PeriodicRunInterceptors

/**
 * The Inara module that provides the application's [PeriodicServices], from which to create its
 * [PeriodicAppService]s:
 *
 * - their default gate is [PeriodicGate.leader] of the [LeaderElectionModule] it requires, so with
 *   `kubernetesLeaderElectionModule()` from boot-leader-k8s only the leader runs them, and with the
 *   `AlwaysLeaderModule` every instance does.
 * - each run is traced, with [periodicTracing], through the OpenTelemetry of the [OpenTelemetryApiModule] it requires,
 *   which with the `NoopOpenTelemetryModule` records nothing.
 * - their interceptors are the tracing, outermost, then the members marked [PeriodicRunInterceptors].
 *
 * Add each loop to the application's services:
 *
 * ```
 * @AppServices val notificationsLoop: PeriodicAppService
 * // bound to Binding.single { periodicServices.create("notifications", notificationsConfig.loop) { notifications.sendPending() } }
 * ```
 */
@DIModule(sets = [PeriodicRunInterceptors::class])
interface PeriodicModule : @DIModule.Required LeaderElectionModule, @DIModule.Required OpenTelemetryApiModule {
  val periodicServices: PeriodicServices
}

/** A [PeriodicModule] on [clock], with each run traced as [tracingOptions] says. */
fun periodicModule(
  clock: Clock = Clock.System,
  tracingOptions: PeriodicTracingOptions = PeriodicTracingOptions(),
): PeriodicModulePart = PeriodicModulePart(
  periodicServices = Binding.single {
    PeriodicServices(
      gate = PeriodicGate.leader(leaderElection),
      clock = clock,
      interceptors = listOfNotNull(tracingOptions.interceptor(openTelemetry)) + setOfPeriodicRunInterceptors(),
    )
  },
)
