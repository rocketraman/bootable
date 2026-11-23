package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.leader.AlwaysLeader
import com.github.rocketraman.bootable.leader.LeaderElection
import io.opentelemetry.api.OpenTelemetry
import org.kodein.di.DI
import org.kodein.di.bindSet
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import org.kodein.di.instanceOrNull
import kotlin.time.Clock

/**
 * A Kodein module that binds the application's [PeriodicServices], from which to create its [PeriodicAppService]s:
 *
 * - their default gate is [PeriodicGate.leader] of the [LeaderElection] binding, if there is one, such as
 *   `kubernetesLeaderElectionModule`'s from boot-leader-k8s, so only the leader runs them; otherwise every instance does.
 * - their clock is the `kotlin.time.Clock` binding, if there is one, or else `Clock.System`.
 * - each run is traced, with [periodicTracing], through the `OpenTelemetry` binding, if there is one, such as
 *   `openTelemetryModule`'s from boot-telemetry-otel-kodein, as the [PeriodicTracingOptions] binding, if there is
 *   one, says.
 * - their interceptors are the tracing, outermost, then the [PeriodicRunInterceptor]s of the set this module opens.
 *
 * Contribute each loop to the app-service set:
 *
 * ```
 * bindAppService { singleton { instance<PeriodicServices>().create("notifications", instance<NotificationsConfig>().loop) { instance<Notifications>().sendPending() } } }
 * ```
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val periodicModule by DI.Module {
  bindSet<PeriodicRunInterceptor>()
  bindSingleton {
    val tracing = (instanceOrNull<PeriodicTracingOptions>() ?: PeriodicTracingOptions())
      .interceptor(instanceOrNull<OpenTelemetry>() ?: OpenTelemetry.noop())
    PeriodicServices(
      gate = PeriodicGate.leader(instanceOrNull<LeaderElection>() ?: AlwaysLeader),
      clock = instanceOrNull<Clock>() ?: Clock.System,
      interceptors = listOfNotNull(tracing) + instance<Set<PeriodicRunInterceptor>>(),
    )
  }
}
