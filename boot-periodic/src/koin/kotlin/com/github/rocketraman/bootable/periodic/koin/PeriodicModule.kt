package com.github.rocketraman.bootable.periodic.koin

import com.github.rocketraman.bootable.leader.AlwaysLeader
import com.github.rocketraman.bootable.leader.LeaderElection
import com.github.rocketraman.bootable.periodic.PeriodicAppService
import com.github.rocketraman.bootable.periodic.PeriodicGate
import com.github.rocketraman.bootable.periodic.PeriodicRunInterceptor
import com.github.rocketraman.bootable.periodic.PeriodicServices
import com.github.rocketraman.bootable.periodic.PeriodicTracingOptions
import com.github.rocketraman.bootable.periodic.periodicTracing
import io.opentelemetry.api.OpenTelemetry
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * A Koin module that defines the application's [PeriodicServices], from which to create its [PeriodicAppService]s:
 *
 * - their default gate is [PeriodicGate.leader] of the [LeaderElection] definition, if there is one, such as
 *   `kubernetesLeaderElectionModule()`'s from boot-leader-k8s, so only the leader runs them; otherwise every instance
 *   does.
 * - their clock is the `kotlin.time.Clock` definition, if there is one, or else `Clock.System`.
 * - each run is traced, with [periodicTracing], through the `OpenTelemetry` definition, if there is one, such as
 *   `openTelemetryModule()`'s from boot-telemetry-otel-koin, as the [PeriodicTracingOptions] definition, if there is
 *   one, says.
 * - their interceptors are the tracing, outermost, then every [PeriodicRunInterceptor] definition.
 *
 * Add each loop to the app services, with a qualifier of its own:
 *
 * ```
 * single<AppService>(named("notifications")) { get<PeriodicServices>().create("notifications", get<NotificationsConfig>().loop) { get<Notifications>().sendPending() } }
 * ```
 */
fun periodicModule(): Module = module {
  single {
    val tracing = (getOrNull<PeriodicTracingOptions>() ?: PeriodicTracingOptions())
      .interceptor(getOrNull<OpenTelemetry>() ?: OpenTelemetry.noop())
    PeriodicServices(
      gate = PeriodicGate.leader(getOrNull<LeaderElection>() ?: AlwaysLeader),
      clock = getOrNull<Clock>() ?: Clock.System,
      interceptors = listOfNotNull(tracing) + getAll<PeriodicRunInterceptor>(),
    )
  }
}
