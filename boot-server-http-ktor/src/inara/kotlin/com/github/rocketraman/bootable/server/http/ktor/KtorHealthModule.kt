@file:JvmName("KtorHealthModuleInara")

package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.BootableModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that provides [KtorHealth], over the `Health` and `BuildInfo` of the `BootableModule` it requires,
 * with its [KtorHealthConfig] decoded from the `health.ktor` config section.
 *
 * Install it in a server with `installHealth(ktorHealth)`. For the routes' listener of their own, add the listener on
 * your engine to the application's services:
 *
 * ```
 * @AppServices val healthListener: AppService   // bound to Binding.single { ktorHealth.listener(CIO) }
 * ```
 */
@DIModule
interface KtorHealthModule : @DIModule.Required BootableModule {
  val ktorHealthConfig: KtorHealthConfig
  val ktorHealth: KtorHealth
}

/** A [KtorHealthModule]. */
fun ktorHealthModule(): KtorHealthModulePart = KtorHealthModulePart(
  // qualified: the ktorHealthConfig member shadows the top-level function
  ktorHealthConfig = Binding.single { com.github.rocketraman.bootable.server.http.ktor.ktorHealthConfig(configBinder) },
  ktorHealth = Binding.single { KtorHealth(health, ktorHealthConfig, buildInfo) },
)
