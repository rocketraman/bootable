package com.github.rocketraman.bootable.boot

import org.kodein.di.*
import org.kodein.di.bindings.DIBinding

fun DI.Builder.bindLifecycleControllerSet() = bindSet<LifecycleController>()

/**
 * Convenience binding function for lifecycle controllers.
 */
@Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
@Deprecated("Use `bindLifecycleController { singleton { ... } }` instead")
fun DI.Builder.bindLifecycleController() = bind<LifecycleController>().inSet()

/**
 * Convenience binding function for lifecycle controllers.
 */
fun DI.Builder.bindLifecycleController(createBinding: () -> DIBinding<*, *, LifecycleController>) =
  inBindSet { add(createBinding) }
