@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.BuildInfo
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.ReadinessConfig
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.koin.bootableModule as koinBootableModule
import com.github.rocketraman.bootable.boot.setOfAppServices
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.server.http.ktor.koin.ktorHealthModule as koinKtorHealthModule
import com.github.rocketraman.bootable.test.StaticConfigBinder
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import io.ktor.server.cio.*
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.instance
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Config with a `health.ktor` section that moves readiness to `/ready`, and puts the routes on a listener of their own
 * at [hostPort], if set, and a `build` section with a version.
 */
private fun healthConfigBinder(hostPort: String? = null): ConfigBinder = StaticConfigBinder(
  "health.ktor" to KtorHealthConfig(readyPath = "/ready", hostPort = hostPort),
  "health.readiness" to ReadinessConfig(),
  "build" to BuildInfo(version = "1.4.2"),
)

sealed interface HealthTestScope

@BindingContainer
object HealthConfigBindings {
  @Provides fun configBinder(): ConfigBinder = healthConfigBinder()
}

@DependencyGraph(HealthTestScope::class, additionalScopes = [BootScope::class], bindingContainers = [HealthConfigBindings::class])
interface HealthGraph : BootableGraph {
  val ktorHealth: KtorHealth
  val health: HealthLifecycleController
}

sealed interface HealthListenerTestScope

/** The routes on a listener of their own, on an application's engine. */
@BindingContainer
object HealthListenerBindings {
  @Provides fun configBinder(): ConfigBinder = healthConfigBinder(hostPort = "127.0.0.1:0")

  @Provides fun ktorServerEngine(): KtorServerEngine = KtorServerEngine(CIO)
}

@DependencyGraph(
  HealthListenerTestScope::class,
  additionalScopes = [BootScope::class],
  bindingContainers = [HealthListenerBindings::class],
)
interface HealthListenerGraph : BootableGraph

/** The listener of its own [services] has, or the service in its place that does nothing. */
private fun Set<AppService>.healthListener(): AppService = single { it.name().startsWith("HealthKtorService") }

/** An application's module, composed as an Inara application composes it. */
@DIModule(composed = true)
interface InaraHealthModule : ConfigModule, BootableModule, KtorServerEngineModule, KtorHealthModule

private fun inaraHealthModule(
  configBinder: ConfigBinder = healthConfigBinder(),
  ktorServerEngineModule: KtorServerEngineModule = NoKtorServerEngineModule,
) = InaraHealthModule(
  configModule = ConfigModule(configBinder = Binding.value(configBinder)),
  bootableModule = bootableModule(),
  ktorServerEngineModule = ktorServerEngineModule,
  ktorHealthModule = ktorHealthModule(),
)

val KtorHealthWiringTest by testSuite {
  test("KtorHealth serves the application's HealthLifecycleController and BuildInfo, with its config, in kodein") {
    val di = DI {
      import(bootableModule)
      import(ktorHealthModule)
      bindSingleton<ConfigBinder> { healthConfigBinder() }
    }.direct
    val ktorHealth = di.instance<KtorHealth>()
    assert(ktorHealth.health === di.instance<HealthLifecycleController>())
    assert(ktorHealth.config.readyPath == "/ready")
    assert(ktorHealth.buildInfo == BuildInfo(version = "1.4.2"))
  }

  test("KtorHealth serves the application's HealthLifecycleController and BuildInfo, with its config, in koin") {
    val koin = koinApplication {
      modules(koinBootableModule(), koinKtorHealthModule(), module { single<ConfigBinder> { healthConfigBinder() } })
    }.koin
    try {
      val ktorHealth = koin.get<KtorHealth>()
      assert(ktorHealth.health === koin.get<HealthLifecycleController>())
      assert(ktorHealth.config.readyPath == "/ready")
      assert(ktorHealth.buildInfo == BuildInfo(version = "1.4.2"))
    } finally {
      koin.close()
    }
  }

  test("KtorHealth serves the application's HealthLifecycleController and BuildInfo, with its config, in metro") {
    val graph = createGraph<HealthGraph>()
    assert(graph.ktorHealth.health === graph.health)
    assert(graph.ktorHealth.config.readyPath == "/ready")
    assert(graph.ktorHealth.buildInfo == BuildInfo(version = "1.4.2"))
  }

  test("KtorHealth serves the application's HealthLifecycleController and BuildInfo, with its config, in inara") {
    val module = inaraHealthModule()
    assert(module.ktorHealth.health === module.health)
    assert(module.ktorHealth.config.readyPath == "/ready")
    assert(module.ktorHealth.buildInfo == BuildInfo(version = "1.4.2"))
  }

  testSuite("the listener of its own is an app service") {
    test("on the application's KtorServerEngine, in kodein") {
      val di = DI {
        import(bootableModule)
        import(ktorHealthModule)
        bindSingleton<ConfigBinder> { healthConfigBinder(hostPort = "127.0.0.1:0") }
        bindSingleton { KtorServerEngine(CIO) }
      }.direct
      assert(di.instance<Set<AppService>>().healthListener() is HealthKtorService<*, *>)
    }

    test("on the application's KtorServerEngine, in koin") {
      val koin = koinApplication {
        modules(
          koinBootableModule(),
          koinKtorHealthModule(),
          module {
            single<ConfigBinder> { healthConfigBinder(hostPort = "127.0.0.1:0") }
            single { KtorServerEngine(CIO) }
          },
        )
      }.koin
      try {
        assert(koin.getAll<AppService>().toSet().healthListener() is HealthKtorService<*, *>)
      } finally {
        koin.close()
      }
    }

    test("on the graph's KtorServerEngine, in metro") {
      assert(createGraph<HealthListenerGraph>().appServices.healthListener() is HealthKtorService<*, *>)
    }

    test("that does nothing, with no engine needed, when no hostPort is set, in kodein") {
      val di = DI {
        import(bootableModule)
        import(ktorHealthModule)
        bindSingleton<ConfigBinder> { healthConfigBinder() }
      }.direct
      assert(di.instance<Set<AppService>>().healthListener().name() == "HealthKtorService (none)")
    }

    test("that does nothing, with no engine needed, when no hostPort is set, in koin") {
      val koin = koinApplication {
        modules(koinBootableModule(), koinKtorHealthModule(), module { single<ConfigBinder> { healthConfigBinder() } })
      }.koin
      try {
        assert(koin.getAll<AppService>().toSet().healthListener().name() == "HealthKtorService (none)")
      } finally {
        koin.close()
      }
    }

    test("that does nothing, with no engine needed, when no hostPort is set, in metro") {
      assert(createGraph<HealthGraph>().appServices.healthListener().name() == "HealthKtorService (none)")
    }

    test("that fails, naming the missing engine, when hostPort is set but there is no engine, in kodein") {
      val di = DI {
        import(bootableModule)
        import(ktorHealthModule)
        bindSingleton<ConfigBinder> { healthConfigBinder(hostPort = "127.0.0.1:0") }
      }.direct
      val failure = runCatching { di.instance<Set<AppService>>() }.exceptionOrNull()
      assert(generateSequence(failure) { it.cause }.any { it.message?.contains("no KtorServerEngine") == true })
    }

    test("that fails, naming the missing engine, when hostPort is set but there is no engine, in koin") {
      val koin = koinApplication {
        modules(
          koinBootableModule(),
          koinKtorHealthModule(),
          module { single<ConfigBinder> { healthConfigBinder(hostPort = "127.0.0.1:0") } },
        )
      }.koin
      try {
        val failure = runCatching { koin.getAll<AppService>() }.exceptionOrNull()
        assert(generateSequence(failure) { it.cause }.any { it.message?.contains("no KtorServerEngine") == true })
      } finally {
        koin.close()
      }
    }

    test("on the application's KtorServerEngine, in inara") {
      val module = inaraHealthModule(
        configBinder = healthConfigBinder(hostPort = "127.0.0.1:0"),
        ktorServerEngineModule = KtorServerEngineModule(ktorServerEngine = Binding.value(KtorServerEngine(CIO))),
      )
      assert(module.setOfAppServices().healthListener() is HealthKtorService<*, *>)
    }

    test("that does nothing, with no engine needed, when no hostPort is set, in inara") {
      assert(inaraHealthModule().setOfAppServices().healthListener().name() == "HealthKtorService (none)")
    }

    test("that fails, naming the missing engine, when hostPort is set but there is no engine, in inara") {
      val module = inaraHealthModule(configBinder = healthConfigBinder(hostPort = "127.0.0.1:0"))
      val failure = runCatching { module.setOfAppServices() }.exceptionOrNull()
      assert(generateSequence(failure) { it.cause }.any { it.message?.contains("no KtorServerEngine") == true })
    }
  }
}
