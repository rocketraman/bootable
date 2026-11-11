@file:JvmName("KtorHealthModuleInara")

package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.AppServices
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that provides [KtorHealth], over the `HealthLifecycleController` and `BuildInfo` of the
 * `BootableModule` it requires, with its [KtorHealthConfig] decoded from the `health.ktor` config section.
 *
 * Install it in a server with `installHealth(ktorHealth)`.
 *
 * It also adds the routes' listener of their own to the app services, which does nothing unless `health.ktor.hostPort`
 * is set, and then runs on the [KtorServerEngine] of the [KtorServerEngineModule] it requires, such as
 * `ktorServerCioEngineModule()`, from boot-server-http-ktor-cio-inara.
 */
@DIModule
interface KtorHealthModule : @DIModule.Required BootableModule, @DIModule.Required KtorServerEngineModule {
  val ktorHealthConfig: KtorHealthConfig
  val ktorHealth: KtorHealth
  @AppServices val ktorHealthListener: AppService
}

/** A [KtorHealthModule]. */
fun ktorHealthModule(): KtorHealthModulePart = KtorHealthModulePart(
  // qualified: the ktorHealthConfig member shadows the top-level function
  ktorHealthConfig = Binding.single { com.github.rocketraman.bootable.server.http.ktor.ktorHealthConfig(configBinder) },
  ktorHealth = Binding.single { KtorHealth(health, ktorHealthConfig, buildInfo) },
  ktorHealthListener = Binding.single { ktorHealth.listener(ktorServerEngine) },
)
