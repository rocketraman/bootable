@file:JvmName("BootableModuleInara")

package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.EmptyConfigBinder
import com.github.rocketraman.bootable.config.EmptyConfigModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module that builds the [Bootable] runtime, composed over the application's [ConfigModule]. Build it with
 * the `BootableModule(…)` builder, pass it to the other Bootable modules' builders, compose it into your application's
 * module, add your own services with [addAppService], and pass [bootable] to `boot { … }`:
 *
 * ```
 * @DIModule(composed = true)
 * interface AppModule : BootableModule {
 *   val greetingService: GreetingService
 * }
 *
 * fun main() = boot {
 *   val app = AppModule(
 *     bootableModule = BootableModule(HopliteConfigModule()),
 *     greetingService = Binding.single { GreetingService() },
 *   )
 *   app.addAppService { app.greetingService }
 *   app.bootable
 * }
 * ```
 *
 * The other Bootable modules' builders, such as `JdbcDataSourceModule(…)`, take this module as their context: they read
 * their config from it, and, since Inara has no multibindings, add their own services or controllers to it, as an
 * [AppServiceAcceptor] or [LifecycleControllerAcceptor].
 */
@DIModule(composed = true)
interface BootableModule : ConfigModule, AppServiceAcceptor, LifecycleControllerAcceptor {
  val appServices: Set<AppService>
  val lifecycleControllers: Set<LifecycleController>
  val startOrder: StartOrder get() = DefaultStartOrder
  val startOrderTiebreaker: StartOrder.Tiebreaker get() = StartOrder.Tiebreaker.ByName

  /** The configured application runtime. Pass it to `boot { … }`. */
  val bootable: Bootable
}

/**
 * A [BootableModule] that runs the services added to it, with the [defaultLifecycleControllers] and any lifecycle
 * controllers added besides.
 *
 * The services start in [startOrder]. Ties are broken by [tiebreaker] if given, else by the tiebreaker of
 * [configModule]'s `boot.service.order` section (see [ServiceOrderConfig]). Without a [configModule], it is the
 * [EmptyConfigModule], and the tiebreaker is [StartOrder.Tiebreaker.ByName].
 */
fun BootableModule(
  configModule: ConfigModule = EmptyConfigModule,
  startOrder: StartOrder = DefaultStartOrder,
  tiebreaker: StartOrder.Tiebreaker? = null,
): BootableModule {
  val services = SetContributions<AppService>()
  val controllers = SetContributions<LifecycleController>()
  return BootableModule(
    configModule = configModule,
    addAppService = Binding.factory { provider -> services += provider },
    addLifecycleController = Binding.factory { provider -> controllers += provider },
    appServices = Binding.single { services.toSet() },
    lifecycleControllers = Binding.single { defaultLifecycleControllers() + controllers.toSet() },
    startOrder = Binding.value(startOrder),
    startOrderTiebreaker = tiebreaker?.let { Binding.value(it) }
      ?: Binding.single {
        if (configBinder === EmptyConfigBinder) StartOrder.Tiebreaker.ByName
        else serviceOrderConfig(configBinder).tiebreaker
      },
    bootable = Binding.single {
      Bootable(appServices, lifecycleControllers, this.startOrder, startOrderTiebreaker)
    },
  )
}
