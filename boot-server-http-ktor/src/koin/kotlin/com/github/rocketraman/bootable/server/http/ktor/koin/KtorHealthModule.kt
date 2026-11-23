package com.github.rocketraman.bootable.server.http.ktor.koin

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.server.http.ktor.KtorHealth
import com.github.rocketraman.bootable.server.http.ktor.KtorHealthConfig
import com.github.rocketraman.bootable.server.http.ktor.KtorServerEngine
import com.github.rocketraman.bootable.server.http.ktor.ktorHealthConfig
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that provides [KtorHealth], over the `HealthLifecycleController` and `BuildInfo` of `bootableModule()`
 * (from boot-koin), with its [KtorHealthConfig] decoded from the `health.ktor` config section. Requires a
 * `ConfigBinder` definition (e.g. from boot-config-hoplite's `hopliteConfigModule()`).
 *
 * Install it in a server with `installHealth(get<KtorHealth>())`.
 *
 * It also adds the routes' listener of their own to the app services, which does nothing unless `health.ktor.hostPort`
 * is set, and then runs on the application's [KtorServerEngine], such as `ktorServerCioEngineModule()`'s, from
 * boot-server-http-ktor-cio-koin.
 */
fun ktorHealthModule(): Module = module {
  single { ktorHealthConfig(get()) }
  single { KtorHealth(get(), get(), get()) }
  single<AppService>(named("ktorHealthListener")) { get<KtorHealth>().listener(getOrNull<KtorServerEngine>()) }
}
