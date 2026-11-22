@file:JvmName("BootableModuleInara")

package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.EmptyConfigBinder
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.inara.DISet

/** Marks a module member as one of the application's services, which the [Bootable] starts and stops. */
@DISet(of = AppService::class)
annotation class AppServices

/** Marks a module member as one of the application's lifecycle controllers, which the [Bootable] runs. */
@DISet(of = LifecycleController::class)
annotation class LifecycleControllers

/** Marks a module member as one of the application's readiness checks, which its [HealthLifecycleController] runs. */
@DISet(of = HealthCheck::class)
annotation class HealthChecks

/**
 * The Inara module that builds the [Bootable] runtime, reading its config from the [ConfigModule] it requires. Build
 * it with `bootableModule()`, compose it into your application's module with a [ConfigModule] and the other Bootable
 * modules, mark your own services [AppServices], and pass [bootable] to `boot { … }`:
 *
 * ```
 * @DIModule(composed = true)
 * interface AppModule : ConfigModule, BootableModule {
 *   @AppServices val greetingService: GreetingService
 * }
 *
 * fun main() = boot {
 *   AppModule(
 *     configModule = hopliteConfigModule(),
 *     bootableModule = bootableModule(),
 *     greetingService = Binding.single { GreetingService() },
 *   ).bootable
 * }
 * ```
 *
 * [bootable] runs every [AppServices] member of the application's module, and the default lifecycle controllers with
 * every [LifecycleControllers] member, including the application's [health], whose checks are the [HealthChecks]
 * members. The other Bootable modules mark their own members the same way.
 *
 * It provides the application's [buildInfo], from the `build` config section, and its [health], with the
 * [ReadinessConfig] of the `health.readiness` config section. With the `EmptyConfigModule`, both are the defaults.
 */
@DIModule(sets = [AppServices::class, LifecycleControllers::class, HealthChecks::class])
interface BootableModule : @DIModule.Required ConfigModule {
  val startOrder: StartOrder
  val startOrderTiebreaker: StartOrder.Tiebreaker
  @LifecycleControllers val appStartStopLifecycleController: AppStartStopLifecycleController
  @LifecycleControllers val stopSignalHandlerLifecycleController: StopSignalHandlerLifecycleController
  @LifecycleControllers val health: HealthLifecycleController
  val buildInfo: BuildInfo

  /** The configured application runtime. Pass it to `boot { … }`. */
  val bootable: Bootable
}

/**
 * A [BootableModule] whose services start in [startOrder]. Ties are broken by [tiebreaker] if given, else by the
 * tiebreaker of the `boot.service.order` config section (see [ServiceOrderConfig]), or, with the `EmptyConfigModule`,
 * by [StartOrder.Tiebreaker.ByName].
 */
fun bootableModule(
  startOrder: StartOrder = DefaultStartOrder,
  tiebreaker: StartOrder.Tiebreaker? = null,
): BootableModulePart = BootableModulePart(
  startOrder = Binding.value(startOrder),
  startOrderTiebreaker = tiebreaker?.let { Binding.value(it) }
    ?: Binding.single {
      if (configBinder === EmptyConfigBinder) StartOrder.Tiebreaker.ByName
      else serviceOrderConfig(configBinder).tiebreaker
    },
  appStartStopLifecycleController = Binding.single { AppStartStopLifecycleController() },
  stopSignalHandlerLifecycleController = Binding.single { StopSignalHandlerLifecycleController() },
  health = Binding.single {
    HealthLifecycleController(
      setOfHealthChecks(),
      if (configBinder === EmptyConfigBinder) ReadinessConfig() else readinessConfig(configBinder),
    )
  },
  // qualified: the buildInfo member shadows the top-level function
  buildInfo = Binding.single {
    if (configBinder === EmptyConfigBinder) BuildInfo()
    else com.github.rocketraman.bootable.boot.buildInfo(configBinder)
  },
  bootable = Binding.single {
    Bootable(setOfAppServices(), setOfLifecycleControllers(), this.startOrder, startOrderTiebreaker)
  },
)
