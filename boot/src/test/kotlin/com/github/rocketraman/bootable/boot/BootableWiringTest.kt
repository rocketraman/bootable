package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.config.ConfigBinder
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.instance
import kotlin.reflect.KClass

sealed interface TestScope

@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class])
interface DefaultsGraph : BootableGraph

private object WiredStartOrder : StartOrder by DefaultStartOrder

@BindingContainer
object OrderBindings {
  @Provides fun startOrder(): StartOrder = WiredStartOrder
  @Provides fun tiebreaker(): StartOrder.Tiebreaker = StartOrder.Tiebreaker.Shuffled(42)
}

/** Config with a `boot.service.order` section that shuffles ties with seed 7. */
private object ShufflingConfig : ConfigBinder {
  override fun <T : Any> configOf(prefix: String, type: KClass<T>): T {
    check(prefix == "boot.service.order" && type == ServiceOrderConfig::class) { "unexpected config $prefix" }
    return type.javaObjectType.cast(ServiceOrderConfig(StartOrder.Tiebreaker.Shuffled(7)))
  }
}

@BindingContainer
object ConfigBindings {
  @Provides fun configBinder(): ConfigBinder = ShufflingConfig
}

@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class], bindingContainers = [ConfigBindings::class])
interface ConfiguredGraph : BootableGraph

// named rather than contributed: a @ContributesTo in test code trips the power-assert bug KT-83931 on the hint file
// Metro generates for it
@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class], bindingContainers = [OrderBindings::class])
interface OrderedGraph : BootableGraph

val BootableWiringTest by testSuite {
  testSuite("the start order and tiebreaker") {
    test("default, in kodein") {
      val bootable = DI { import(bootableModule) }.direct.instance<Bootable>()
      assert(bootable.startOrder === DefaultStartOrder && bootable.tiebreaker == StartOrder.Tiebreaker.ByName)
    }

    test("are bound, in kodein") {
      val bootable = DI {
        import(bootableModule)
        bindSingleton<StartOrder> { WiredStartOrder }
        bindSingleton<StartOrder.Tiebreaker> { StartOrder.Tiebreaker.Shuffled(42) }
      }.direct.instance<Bootable>()
      assert(bootable.startOrder === WiredStartOrder && bootable.tiebreaker == StartOrder.Tiebreaker.Shuffled(42))
    }

    test("from config, in kodein") {
      val bootable = DI {
        import(bootableModule)
        bindSingleton<ConfigBinder> { ShufflingConfig }
      }.direct.instance<Bootable>()
      assert(bootable.tiebreaker == StartOrder.Tiebreaker.Shuffled(7))
    }

    test("default, in metro") {
      val bootable = createGraph<DefaultsGraph>().bootable
      assert(bootable.startOrder === DefaultStartOrder && bootable.tiebreaker == StartOrder.Tiebreaker.ByName)
    }

    test("from config, in metro") {
      assert(createGraph<ConfiguredGraph>().bootable.tiebreaker == StartOrder.Tiebreaker.Shuffled(7))
    }

    test("are bound, in metro") {
      val bootable = createGraph<OrderedGraph>().bootable
      assert(bootable.startOrder === WiredStartOrder && bootable.tiebreaker == StartOrder.Tiebreaker.Shuffled(42))
    }
  }
}
