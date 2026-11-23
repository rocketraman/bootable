package com.github.rocketraman.bootable.boot.koin

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.AppStartStopLifecycleController
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.BuildInfo
import com.github.rocketraman.bootable.boot.DefaultStartOrder
import com.github.rocketraman.bootable.boot.HealthCheck
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.LifecycleController
import com.github.rocketraman.bootable.boot.ReadinessConfig
import com.github.rocketraman.bootable.boot.ServiceOrderConfig
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.StopSignalHandlerLifecycleController
import com.github.rocketraman.bootable.boot.buildInfo
import com.github.rocketraman.bootable.boot.readinessConfig
import com.github.rocketraman.bootable.boot.serviceOrderConfig
import com.github.rocketraman.bootable.config.ConfigBinder
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that wires the (vanilla) [Bootable] runtime and the default lifecycle controllers.
 *
 * Koin has no set multibindings: the [Bootable] runs every [AppService] and every [LifecycleController] definition of
 * the application, as Koin's `getAll` finds them. Give each a qualifier of its own, so that it adds to the others
 * rather than overriding them:
 *
 * ```
 * single<AppService>(named("greeting")) { GreetingService() }
 * ```
 *
 * A [StartOrder] or [StartOrder.Tiebreaker] definition, if there is one, is passed to [Bootable]. Otherwise it uses
 * [DefaultStartOrder], and the [StartOrder.Tiebreaker] of the `boot.service.order` config section (see
 * [ServiceOrderConfig]) if there is a [ConfigBinder] definition, or else [StartOrder.Tiebreaker.ByName].
 *
 * It defines the application's [HealthLifecycleController], and adds it to the lifecycle controllers, with every
 * [HealthCheck] definition as its checks, and the [ReadinessConfig] of the `health.readiness` config section if there
 * is a [ConfigBinder] definition. Add a readiness check with `single<HealthCheck>(named("my-check")) { MyCheck() }`.
 *
 * It defines the application's [BuildInfo], from the `build` config section if there is a [ConfigBinder] definition.
 *
 * Each of Bootable's Koin modules is a function, as Koin keeps a module's single instances in the module itself: each
 * call is a new module, so each Koin application, such as each test's, gets instances of its own.
 */
fun bootableModule(): Module = module {
  single {
    val config = getOrNull<ConfigBinder>()?.let { readinessConfig(it) } ?: ReadinessConfig()
    HealthLifecycleController(getAll<HealthCheck>().toSet(), config)
  }
  single { getOrNull<ConfigBinder>()?.let { buildInfo(it) } ?: BuildInfo() }
  single<LifecycleController>(named<AppStartStopLifecycleController>()) { AppStartStopLifecycleController() }
  single<LifecycleController>(named<StopSignalHandlerLifecycleController>()) { StopSignalHandlerLifecycleController() }
  single<LifecycleController>(named<HealthLifecycleController>()) { get<HealthLifecycleController>() }
  single {
    val tiebreaker = getOrNull<StartOrder.Tiebreaker>()
      ?: getOrNull<ConfigBinder>()?.let { serviceOrderConfig(it).tiebreaker }
      ?: StartOrder.Tiebreaker.ByName
    Bootable(
      getAll<AppService>().toSet(),
      getAll<LifecycleController>().toSet(),
      getOrNull<StartOrder>() ?: DefaultStartOrder,
      tiebreaker,
    )
  }
}
