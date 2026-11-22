package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.BuildInfo
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.ReadinessConfig
import com.github.rocketraman.bootable.boot.bootableModule
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

/** Config with a `health.ktor` section that moves readiness to `/ready`, and a `build` section with a version. */
private object HealthConfigBinder : ConfigBinder {
  override fun <T : Any> configOf(prefix: String, type: KClass<T>): T = type.javaObjectType.cast(
    when (type) {
      KtorHealthConfig::class -> KtorHealthConfig(readyPath = "/ready")
      ReadinessConfig::class -> ReadinessConfig()
      BuildInfo::class -> BuildInfo(version = "1.4.2")
      else -> error("unexpected config $prefix")
    }
  )
}

sealed interface HealthTestScope

@BindingContainer
object HealthConfigBindings {
  @Provides fun configBinder(): ConfigBinder = HealthConfigBinder
}

@DependencyGraph(HealthTestScope::class, additionalScopes = [BootScope::class], bindingContainers = [HealthConfigBindings::class])
interface HealthGraph : BootableGraph {
  val ktorHealth: KtorHealth
  val health: HealthLifecycleController
}

val KtorHealthWiringTest by testSuite {
  test("KtorHealth serves the application's HealthLifecycleController and BuildInfo, with its config, in kodein") {
    val di = DI {
      import(bootableModule)
      import(ktorHealthModule)
      bindSingleton<ConfigBinder> { HealthConfigBinder }
    }.direct
    val ktorHealth = di.instance<KtorHealth>()
    assert(ktorHealth.health === di.instance<HealthLifecycleController>())
    assert(ktorHealth.config.readyPath == "/ready")
    assert(ktorHealth.buildInfo == BuildInfo(version = "1.4.2"))
  }

  test("KtorHealth serves the application's HealthLifecycleController and BuildInfo, with its config, in metro") {
    val graph = createGraph<HealthGraph>()
    assert(graph.ktorHealth.health === graph.health)
    assert(graph.ktorHealth.config.readyPath == "/ready")
    assert(graph.ktorHealth.buildInfo == BuildInfo(version = "1.4.2"))
  }
}
