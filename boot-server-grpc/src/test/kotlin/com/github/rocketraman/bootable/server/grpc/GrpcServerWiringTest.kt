@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.server.grpc

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.setOfAppServices
import com.github.rocketraman.bootable.boot.koin.bootableModule as koinBootableModule
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.server.grpc.koin.grpcServerModule as koinGrpcServerModule
import com.github.rocketraman.bootable.test.StaticConfigBinder
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import io.grpc.BindableService
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.instance
import org.kodein.di.singleton
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

private fun grpcConfigBinder(): ConfigBinder = StaticConfigBinder(
  "grpc.server" to GrpcServerConfig(hostPort = ":9999"),
  "health.grpc" to GrpcHealthConfig(livenessService = "live"),
  "health.readiness" to com.github.rocketraman.bootable.boot.ReadinessConfig(),
  "build" to com.github.rocketraman.bootable.boot.BuildInfo(),
  "boot.service.order" to com.github.rocketraman.bootable.boot.ServiceOrderConfig(),
)

sealed interface GrpcTestScope

@BindingContainer
object GrpcTestBindings {
  @Provides fun configBinder(): ConfigBinder = grpcConfigBinder()
  @Provides @IntoSet fun greeter(): BindableService = GreeterService()
}

@DependencyGraph(GrpcTestScope::class, additionalScopes = [BootScope::class], bindingContainers = [GrpcTestBindings::class])
interface GrpcGraph : BootableGraph {
  val grpcHealth: GrpcHealth
  val grpcServices: Set<BindableService>
  val health: HealthLifecycleController
}

/** An application's module, composed as an Inara application composes it, with a service of its own. */
@DIModule(composed = true)
interface InaraGrpcModule : ConfigModule, BootableModule, GrpcServerModule {
  @GrpcServices val greeterService: GreeterService
}

val GrpcServerWiringTest by testSuite {
  test("the GrpcServer is an app service, with its config and the application's services, in kodein") {
    val di = DI {
      import(bootableModule)
      import(grpcServerModule)
      bindSingleton<ConfigBinder> { grpcConfigBinder() }
      bindGrpcService { singleton { GreeterService() } }
    }.direct
    val grpc = di.instance<Set<AppService>>().filterIsInstance<GrpcServer>().single()
    assert(grpc.config.hostPort == ":9999")
    assert(di.instance<Set<BindableService>>().single() is GreeterService)
    assert(di.instance<GrpcHealth>().health === di.instance<HealthLifecycleController>())
    assert(di.instance<GrpcHealth>().config.livenessService == "live")
  }

  test("the GrpcServer is an app service, with its config and the application's services, in metro") {
    val graph = createGraph<GrpcGraph>()
    val grpc = graph.appServices.filterIsInstance<GrpcServer>().single()
    assert(grpc.config.hostPort == ":9999")
    assert(graph.grpcServices.single() is GreeterService)
    assert(graph.grpcHealth.health === graph.health)
    assert(graph.grpcHealth.config.livenessService == "live")
  }

  test("the GrpcServer is an app service, with its config and the application's services, in koin") {
    val koin = koinApplication {
      modules(
        koinBootableModule(),
        koinGrpcServerModule(),
        module {
          single<ConfigBinder> { grpcConfigBinder() }
          single<BindableService>(named("greeter")) { GreeterService() }
        },
      )
    }.koin
    try {
      val grpc = koin.getAll<AppService>().filterIsInstance<GrpcServer>().single()
      assert(grpc.config.hostPort == ":9999")
      assert(koin.getAll<BindableService>().single() is GreeterService)
      assert(koin.get<GrpcHealth>().health === koin.get<HealthLifecycleController>())
      assert(koin.get<GrpcHealth>().config.livenessService == "live")
    } finally {
      koin.close()
    }
  }

  test("the GrpcServer is an app service, with its config and the application's services, in inara") {
    val module = InaraGrpcModule(
      configModule = ConfigModule(configBinder = Binding.value(grpcConfigBinder())),
      bootableModule = bootableModule(),
      grpcServerModule = grpcServerModule(),
      greeterService = Binding.single { GreeterService() },
    )
    val grpc = module.setOfAppServices().filterIsInstance<GrpcServer>().single()
    assert(grpc === module.grpcServer)
    assert(grpc.config.hostPort == ":9999")
    assert(module.setOfGrpcServices().single() === module.greeterService)
    assert(module.grpcHealth.health === module.health)
    assert(module.grpcHealth.config.livenessService == "live")
  }
}
