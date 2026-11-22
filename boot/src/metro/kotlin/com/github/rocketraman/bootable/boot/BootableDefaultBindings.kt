package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.config.ConfigBinder
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass

/**
 * Provides the [Bootable] runtime and the default lifecycle controllers
 * ([AppStartStopLifecycleController], [StopSignalHandlerLifecycleController]).
 *
 * Contributed to [BootScope], so a magic-mode graph (`additionalScopes = [BootScope::class]`) picks it up with
 * no boilerplate. To run a custom lifecycle, don't include this container — provide your own
 * `@IntoSet LifecycleController` bindings; you still get the [Bootable] runtime.
 *
 * A [StartOrder] or [StartOrder.Tiebreaker] binding, if the graph has one, is passed to [Bootable]. Otherwise it uses
 * [DefaultStartOrder], and the [StartOrder.Tiebreaker] of the `boot.service.order` config section (see
 * [ServiceOrderConfig]) if the graph has a [ConfigBinder], or else [StartOrder.Tiebreaker.ByName]. All three are
 * optional bindings, so a graph needs none of them.
 *
 * It provides the application's [HealthLifecycleController], and adds it to the lifecycle controllers, with the checks
 * of the graph's `Set<HealthCheck>`, and the [ReadinessConfig] of the `health.readiness` config section if the graph
 * has a [ConfigBinder]. Add a readiness check with `@Provides @IntoSet fun myCheck(): HealthCheck = MyCheck()`.
 *
 * It provides the application's [BuildInfo], from the `build` config section if the graph has a [ConfigBinder].
 */
@ContributesTo(BootScope::class)
@BindingContainer
object BootableDefaultBindings {
  @Provides @IntoSet fun appStartStop(): LifecycleController = AppStartStopLifecycleController()
  @Provides @IntoSet fun stopSignalHandler(): LifecycleController = StopSignalHandlerLifecycleController()
  @Provides @IntoSet fun healthController(health: HealthLifecycleController): LifecycleController = health

  @Provides @SingleIn(BootScope::class)
  fun health(
    checks: Set<HealthCheck>,
    configBinder: ConfigBinder = NoConfigBinder,
  ): HealthLifecycleController = HealthLifecycleController(
    checks,
    if (configBinder === NoConfigBinder) ReadinessConfig() else readinessConfig(configBinder),
  )

  @Provides @SingleIn(BootScope::class)
  // not named buildInfo, which would shadow the top-level function it calls
  fun appBuildInfo(configBinder: ConfigBinder = NoConfigBinder): BuildInfo =
    if (configBinder === NoConfigBinder) BuildInfo() else buildInfo(configBinder)

  @Provides @SingleIn(BootScope::class)
  fun bootable(
    appServices: Set<AppService>,
    lifecycleControllers: Set<LifecycleController>,
    startOrder: StartOrder = DefaultStartOrder,
    // non-null, with a stand-in default: Metro would look for a `ConfigBinder?` binding, which nothing provides
    configBinder: ConfigBinder = NoConfigBinder,
    tiebreaker: StartOrder.Tiebreaker = when {
      configBinder === NoConfigBinder -> StartOrder.Tiebreaker.ByName
      else -> serviceOrderConfig(configBinder).tiebreaker
    },
  ): Bootable = Bootable(appServices, lifecycleControllers, startOrder, tiebreaker)

  /** Stands in for a graph's missing [ConfigBinder]; never asked for config. */
  private object NoConfigBinder : ConfigBinder {
    override fun <T : Any> configOf(prefix: String, type: KClass<T>): T = error("No ConfigBinder is bound")
  }
}
