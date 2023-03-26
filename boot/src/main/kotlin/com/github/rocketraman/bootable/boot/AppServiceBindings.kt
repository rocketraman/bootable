package com.github.rocketraman.bootable.boot

import org.kodein.di.*
import org.kodein.di.bindings.DIBinding

fun DI.Builder.bindAppServiceSet() = bindSet<AppService>()

/**
 * Convenience binding function for application services. Use it like this in a module:
 *
 * ```
 * bindAppService() with singleton { Whatever() }
 * ```
 */
@Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
@Deprecated("Use `bindAppService { singleton { ... } }` instead")
fun DI.Builder.bindAppService() = bind<AppService>().inSet()

/**
 * Convenience binding function for application services. Use it like this in a module:
 *
 * ```
 * bindAppService { singleton { Whatever() } }
 * ```
 */
fun DI.Builder.bindAppService(createBinding: () -> DIBinding<*, *, AppService>) = inBindSet { add(createBinding) }
