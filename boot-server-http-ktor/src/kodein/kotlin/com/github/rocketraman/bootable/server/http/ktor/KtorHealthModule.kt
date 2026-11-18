package com.github.rocketraman.bootable.server.http.ktor

import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance

/**
 * A Kodein module that provides [KtorHealth], over the `Health` of `bootableModule` (from boot-kodein), with its
 * [KtorHealthConfig] decoded from the `health.ktor` config section. Requires a `ConfigBinder` binding (e.g. from
 * boot-config-hoplite's `hopliteConfigModule`).
 *
 * Install it in a server with `installHealth(instance<KtorHealth>())`. For the routes' listener of their own, add
 * the listener on your engine to the app services:
 *
 * ```
 * bindAppService { singleton { instance<KtorHealth>().listener(CIO) } }
 * ```
 */
val ktorHealthModule by DI.Module {
  bindSingleton { ktorHealthConfig(instance()) }
  bindSingleton { KtorHealth(instance(), instance()) }
}
