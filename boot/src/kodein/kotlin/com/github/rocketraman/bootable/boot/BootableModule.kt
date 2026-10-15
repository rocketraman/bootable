package com.github.rocketraman.bootable.boot

import org.kodein.di.DI
import org.kodein.di.bindSet
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that wires the (vanilla) [Bootable] runtime and the default lifecycle controllers, and opens
 * the `Set<AppService>` / `Set<LifecycleController>` multibindings for your own modules to contribute into.
 */
val bootableModule by DI.Module {
  bindSet<AppService>()
  bindSet<LifecycleController>()
  inBindSet<LifecycleController> {
    add { singleton { AppStartStopLifecycleController() } }
    add { singleton { StopSignalHandlerLifecycleController() } }
  }
  bindSingleton { Bootable(instance(), instance()) }
}

/** Contribute an [AppService] into the boot set: `bindAppService { singleton { MyService() } }`. */
fun DI.Builder.bindAppService(createBinding: () -> org.kodein.di.bindings.DIBinding<*, *, out AppService>) =
  inBindSet<AppService> { add(createBinding) }
