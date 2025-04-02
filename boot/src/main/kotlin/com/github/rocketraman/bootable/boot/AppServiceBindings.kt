package com.github.rocketraman.bootable.boot

import org.kodein.di.*
import org.kodein.di.bindings.DIBinding

internal fun DI.Builder.bindAppServiceSet() = bindSet<AppService>()

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
 *
 * See also [bindAppServiceWith] if the type itself should be bound as well.
 */
fun DI.Builder.bindAppService(createBinding: () -> DIBinding<*, *, out AppService>) =
  inBindSet { add(createBinding) }

/**
 * Convenience binding function for binding the given binding as an [AppService], as well as the given type T.
 *
 * ```
 * bindAsAppService<Whatever> { singleton { Whatever() } }
 * ```
 *
 * See [bindAppService] if the type itself does not need to be bound.
 */
inline fun <reified T: AppService> DI.Builder.bindAppServiceWith(crossinline createBinding: () -> DIBinding<*, *, out T>) {
  createBinding().let {
    bind<T>() with it
    inBindSet<AppService> { add { it } }
  }
}
