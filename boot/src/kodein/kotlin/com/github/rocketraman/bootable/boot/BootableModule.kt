package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.config.ConfigBinder
import org.kodein.di.DI
import org.kodein.di.bindSet
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.instanceOrNull
import org.kodein.di.singleton

/**
 * A Kodein module that wires the (vanilla) [Bootable] runtime and the default lifecycle controllers, and opens
 * the `Set<AppService>` / `Set<LifecycleController>` multibindings for your own modules to contribute into.
 *
 * A [StartOrder] or [StartOrder.Tiebreaker] binding, if there is one, is passed to [Bootable]. Otherwise it uses
 * [DefaultStartOrder], and the [StartOrder.Tiebreaker] of the `boot.service.order` config section (see
 * [ServiceOrderConfig]) if there is a [ConfigBinder] binding, or else [StartOrder.Tiebreaker.ByName].
 *
 * It binds the application's [HealthLifecycleController], and adds it to the lifecycle controllers, with the checks of
 * the `Set<HealthCheck>` it opens, and the [ReadinessConfig] of the `health.readiness` config section if there is a
 * [ConfigBinder] binding. Add a readiness check with `inBindSet<HealthCheck> { add { singleton { MyCheck() } } }`.
 *
 * It binds the application's [BuildInfo], from the `build` config section if there is a [ConfigBinder] binding.
 */
val bootableModule by DI.Module {
  bindSet<AppService>()
  bindSet<LifecycleController>()
  bindSet<HealthCheck>()
  bindSingleton {
    val config = instanceOrNull<ConfigBinder>()?.let { readinessConfig(it) } ?: ReadinessConfig()
    HealthLifecycleController(instance(), config)
  }
  bindSingleton { instanceOrNull<ConfigBinder>()?.let { buildInfo(it) } ?: BuildInfo() }
  inBindSet<LifecycleController> {
    add { singleton { AppStartStopLifecycleController() } }
    add { singleton { StopSignalHandlerLifecycleController() } }
    add { singleton { instance<HealthLifecycleController>() } }
  }
  bindSingleton {
    val tiebreaker = instanceOrNull<StartOrder.Tiebreaker>()
      ?: instanceOrNull<ConfigBinder>()?.let { serviceOrderConfig(it).tiebreaker }
      ?: StartOrder.Tiebreaker.ByName
    Bootable(instance(), instance(), instanceOrNull() ?: DefaultStartOrder, tiebreaker)
  }
}

/** Contribute an [AppService] into the boot set: `bindAppService { singleton { MyService() } }`. */
fun DI.Builder.bindAppService(createBinding: () -> org.kodein.di.bindings.DIBinding<*, *, out AppService>) =
  inBindSet<AppService> { add(createBinding) }
