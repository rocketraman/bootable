package com.github.rocketraman.bootable.boot

import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.bindSet
import org.kodein.di.inSet

fun DI.Builder.bindLifecycleControllerSet() = bindSet<LifecycleController>()

/**
 * Convenience binding function for lifecycle controllers.
 */
fun DI.Builder.bindLifecycleController() = bind<LifecycleController>().inSet()
