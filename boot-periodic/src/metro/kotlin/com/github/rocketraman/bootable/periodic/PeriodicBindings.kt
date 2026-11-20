package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.leader.AlwaysLeader
import com.github.rocketraman.bootable.leader.LeaderElection
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.opentelemetry.api.OpenTelemetry
import kotlin.time.Clock

/**
 * Provides the application's [PeriodicServices], from which to create its [PeriodicAppService]s:
 *
 * - their default gate is [PeriodicGate.leader] of the graph's [LeaderElection], if it has one, such as
 *   boot-leader-k8s-metro's, so only the leader runs them; otherwise every instance does.
 * - their clock is the graph's `kotlin.time.Clock`, if it has one, or else `Clock.System`.
 * - each run is traced, with [periodicTracing], through the graph's `OpenTelemetry`, if it has one, such as
 *   boot-telemetry-otel-metro's, as the graph's [PeriodicTracingOptions], if it has one, says.
 * - their interceptors are the tracing, outermost, then the contributed [PeriodicRunInterceptor]s.
 *
 * Contribute each loop to the app-service set:
 *
 * ```
 * @Provides @IntoSet
 * fun notificationsLoop(periodic: PeriodicServices, config: NotificationsConfig, notifications: Notifications): AppService =
 *   periodic.create("notifications", config.loop) { notifications.sendPending() }
 * ```
 */
@ContributesTo(BootScope::class)
interface PeriodicBindings {
  @Multibinds(allowEmpty = true) val periodicRunInterceptors: Set<PeriodicRunInterceptor>

  companion object {
    @Provides @SingleIn(BootScope::class)
    fun periodicServices(
      interceptors: Set<PeriodicRunInterceptor>,
      leaderElection: LeaderElection = AlwaysLeader,
      clock: Clock = Clock.System,
      openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
      tracingOptions: PeriodicTracingOptions = PeriodicTracingOptions(),
    ): PeriodicServices = PeriodicServices(
      PeriodicGate.leader(leaderElection),
      clock,
      listOfNotNull(tracingOptions.interceptor(openTelemetry)) + interceptors,
    )
  }
}
