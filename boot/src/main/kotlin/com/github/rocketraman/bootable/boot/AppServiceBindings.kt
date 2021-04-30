package com.github.rocketraman.bootable.boot

import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.bindSet
import org.kodein.di.inSet

fun DI.Builder.bindAppServiceSet() = bindSet<AppService>()

/**
 * Convenience binding function for application services.
 */
fun DI.Builder.bindAppService() = bind<AppService>().inSet()
