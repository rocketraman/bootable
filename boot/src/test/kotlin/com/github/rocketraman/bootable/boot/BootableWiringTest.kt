package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.test.StaticConfigBinder
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.inBindSet
import org.kodein.di.singleton
import org.kodein.di.instance
import kotlin.time.Duration.Companion.seconds

sealed interface TestScope

@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class])
interface DefaultsGraph : BootableGraph {
  val buildInfo: BuildInfo
}

private object WiredStartOrder : StartOrder by DefaultStartOrder

@BindingContainer
object OrderBindings {
  @Provides fun startOrder(): StartOrder = WiredStartOrder
  @Provides fun tiebreaker(): StartOrder.Tiebreaker = StartOrder.Tiebreaker.Shuffled(42)
}

/** Config with a `boot.service.order` section that shuffles ties with seed 7, a 3s drain delay, and a version. */
private val ShufflingConfig = StaticConfigBinder(
  "boot.service.order" to ServiceOrderConfig(StartOrder.Tiebreaker.Shuffled(7)),
  "health.readiness" to ReadinessConfig(drainDelay = 3.seconds),
  "build" to BuildInfo(version = "1.4.2"),
)

@BindingContainer
object ConfigBindings {
  @Provides fun configBinder(): ConfigBinder = ShufflingConfig
}

@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class], bindingContainers = [ConfigBindings::class])
interface ConfiguredGraph : BootableGraph {
  val buildInfo: BuildInfo
}

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

  testSuite("health") {
    fun Set<LifecycleController>.health() = filterIsInstance<HealthLifecycleController>().single()
    val check = HealthCheck("wired") { HealthStatus.Healthy }

    test("is a lifecycle controller, with the bound checks and config, in kodein") {
      val di = DI {
        import(bootableModule)
        bindSingleton<ConfigBinder> { ShufflingConfig }
        inBindSet<HealthCheck> { add { singleton { check } } }
      }.direct
      val health = di.instance<Set<LifecycleController>>().health()
      assert(health === di.instance<HealthLifecycleController>())
      assert(health.config.drainDelay == 3.seconds)
      assert(di.instance<Set<HealthCheck>>() == setOf(check))
    }

    test("is a lifecycle controller, in metro") {
      val graph = createGraph<ConfiguredGraph>()
      assert(graph.lifecycleControllers.health().config.drainDelay == 3.seconds)
      assert(graph.healthChecks.isEmpty())
    }
  }

  testSuite("build info") {
    test("from config, in kodein") {
      val di = DI {
        import(bootableModule)
        bindSingleton<ConfigBinder> { ShufflingConfig }
      }.direct
      assert(di.instance<BuildInfo>() == BuildInfo(version = "1.4.2"))
    }

    test("empty without config, in kodein") {
      assert(DI { import(bootableModule) }.direct.instance<BuildInfo>() == BuildInfo())
    }

    test("from config, in metro") {
      assert(createGraph<ConfiguredGraph>().buildInfo == BuildInfo(version = "1.4.2"))
    }

    test("empty without config, in metro") {
      assert(createGraph<DefaultsGraph>().buildInfo == BuildInfo())
    }
  }
}
