package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BuildInfo
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.config.ConfigBinder
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Provides [KtorHealth], over the `HealthLifecycleController` and `BuildInfo` of `BootableDefaultBindings` (from
 * boot-metro), with its [KtorHealthConfig] decoded from the `health.ktor` config section.
 *
 * Install it in a server with `installHealth(health)`.
 *
 * It also contributes the routes' listener of their own to the app services, which does nothing unless
 * `health.ktor.hostPort` is set, and then runs on the graph's [KtorServerEngine], such as
 * `KtorServerCioEngineBindings`'s, from boot-server-http-ktor-cio-metro.
 */
@ContributesTo(BootScope::class)
interface KtorHealthBindings {
  companion object {
    @Provides @SingleIn(BootScope::class)
    fun healthConfig(binder: ConfigBinder): KtorHealthConfig = ktorHealthConfig(binder)

    @Provides @SingleIn(BootScope::class)
    fun ktorHealth(health: HealthLifecycleController, config: KtorHealthConfig, buildInfo: BuildInfo): KtorHealth =
      KtorHealth(health, config, buildInfo)

    @Provides @IntoSet @SingleIn(BootScope::class)
    fun healthListener(health: KtorHealth, engine: KtorServerEngine = NoKtorServerEngine): AppService =
      health.listener(engine.takeUnless { it === NoKtorServerEngine })
  }
}

/**
 * In place of the graph's [KtorServerEngine], when it has none: Metro takes a default value for a binding it doesn't
 * have, but a nullable parameter would be a binding of its own, which the engine's isn't.
 */
private object NoKtorServerEngine : KtorServerEngine {
  override val factory get() = error("no KtorServerEngine")
}
