package com.github.rocketraman.bootable.boot

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Provides the [Bootable] runtime and the default lifecycle controllers
 * ([AppStartStopLifecycleController], [StopSignalHandlerLifecycleController]).
 *
 * Contributed to [BootScope], so a magic-mode graph (`additionalScopes = [BootScope::class]`) picks it up with
 * no boilerplate. To run a custom lifecycle, don't include this container — provide your own
 * `@IntoSet LifecycleController` bindings; you still get the [Bootable] runtime.
 */
@ContributesTo(BootScope::class)
@BindingContainer
object BootableDefaultBindings {
  @Provides @IntoSet fun appStartStop(): LifecycleController = AppStartStopLifecycleController()
  @Provides @IntoSet fun stopSignalHandler(): LifecycleController = StopSignalHandlerLifecycleController()

  @Provides @SingleIn(BootScope::class)
  fun bootable(appServices: Set<AppService>, lifecycleControllers: Set<LifecycleController>): Bootable =
    Bootable(appServices, lifecycleControllers)
}
