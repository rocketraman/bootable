package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BuildInfo
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.config.ConfigBinder
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Provides [KtorHealth], over the `HealthLifecycleController` and `BuildInfo` of `BootableDefaultBindings` (from
 * boot-metro), with its [KtorHealthConfig] decoded from the `health.ktor` config section.
 *
 * Install it in a server with `installHealth(health)`. For the routes' listener of their own, add the listener on
 * your engine to the app services:
 *
 * ```
 * @Provides @IntoSet fun healthListener(health: KtorHealth): AppService = health.listener(CIO)
 * ```
 */
@ContributesTo(BootScope::class)
interface KtorHealthBindings {
  companion object {
    @Provides @SingleIn(BootScope::class)
    fun healthConfig(binder: ConfigBinder): KtorHealthConfig = ktorHealthConfig(binder)

    @Provides @SingleIn(BootScope::class)
    fun ktorHealth(health: HealthLifecycleController, config: KtorHealthConfig, buildInfo: BuildInfo): KtorHealth =
      KtorHealth(health, config, buildInfo)
  }
}
