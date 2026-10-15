package com.github.rocketraman.bootable.env

import com.github.rocketraman.bootable.boot.LifecycleController
import com.github.rocketraman.bootable.config.ConfigBinder

/**
 * Initializes the global [environment] from [EnvironmentConfig].
 *
 * Kodein had an `onReady { ... }` hook that ran once the graph was fully constructed. Metro has no runtime
 * "graph ready" callback (the graph is generated code, not an assembled runtime object), so we express the same
 * "run once, before the app starts" semantics using Bootable's own lifecycle: a [LifecycleController] whose
 * [appInit] runs before any service is started, at the highest priority. This is arguably a better fit — the
 * side effect now lives inside the framework's documented lifecycle instead of a DI-container callback.
 *
 * A plain class with no DI annotations; it is registered by [EnvironmentBindings] and can be constructed
 * directly in a unit test.
 */
class EnvironmentInitializer(
  private val config: EnvironmentConfig,
) : LifecycleController {
  override fun priority() = Int.MAX_VALUE

  override fun appInit() {
    environment = Env.fromString(config.environment)
  }
}

/**
 * A [LifecycleController] that initializes the global [environment] from configuration before services start.
 * Add it to the set passed to `boot`:
 *
 * ```
 * boot(
 *   appServices = ...,
 *   lifecycleControllers = defaultLifecycleControllers() + environmentInitializer(binder),
 * )
 * ```
 */
fun environmentInitializer(binder: ConfigBinder): LifecycleController =
  EnvironmentInitializer(environmentConfig(binder))
