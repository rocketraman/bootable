package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.bindAppService
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import org.kodein.di.instanceOrNull
import org.kodein.di.singleton

/**
 * A Kodein module that provides [KtorHealth], over the `HealthLifecycleController` and `BuildInfo` of `bootableModule`
 * (from boot-kodein), with its [KtorHealthConfig] decoded from the `health.ktor` config section. Requires a
 * `ConfigBinder` binding (e.g. from boot-config-hoplite's `hopliteConfigModule`).
 *
 * Install it in a server with `installHealth(instance<KtorHealth>())`.
 *
 * It also adds the routes' listener of their own to the app services, which does nothing unless `health.ktor.hostPort`
 * is set, and then runs on the application's [KtorServerEngine], such as `ktorServerCioEngineModule`'s, from
 * boot-server-http-ktor-cio-kodein.
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val ktorHealthModule by DI.Module {
  bindSingleton { ktorHealthConfig(instance()) }
  bindSingleton { KtorHealth(instance(), instance(), instance()) }
  bindAppService { singleton { instance<KtorHealth>().listener(instanceOrNull<KtorServerEngine>()) } }
}
