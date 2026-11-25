@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.server.grpc

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.koin.bootableModule as koinBootableModule
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.server.grpc.koin.grpcServerModule as koinGrpcServerModule
import com.github.rocketraman.bootable.server.grpc.koin.grpcTelemetryModule as koinGrpcTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.NoopOpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryApiModule
import com.github.rocketraman.bootable.test.StaticConfigBinder
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import io.opentelemetry.api.OpenTelemetry
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.inBindSet
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.instance
import org.kodein.di.singleton
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

private fun telemetryConfigBinder(): ConfigBinder = StaticConfigBinder(
  "telemetry.grpc" to GrpcTelemetryConfig(traceHealth = true),
  "grpc.server" to GrpcServerConfig(),
  "health.grpc" to GrpcHealthConfig(),
  "health.readiness" to com.github.rocketraman.bootable.boot.ReadinessConfig(),
  "build" to com.github.rocketraman.bootable.boot.BuildInfo(),
  "boot.service.order" to com.github.rocketraman.bootable.boot.ServiceOrderConfig(),
)

/** A [GrpcServerTelemetryCustomizer] that records whether the instrumentation was built with it. */
class RecordingTelemetryCustomizer : GrpcServerTelemetryCustomizer {
  var customized = false
  override fun customize(builder: io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetryBuilder) {
    customized = true
  }
}

/** Checks [telemetry] is one of the server's [customizers], with its config, over [openTelemetry] and [customizer]. */
private fun checkTelemetry(
  telemetry: GrpcTelemetry,
  customizers: Set<GrpcServerCustomizer>,
  openTelemetry: OpenTelemetry,
  customizer: RecordingTelemetryCustomizer,
) {
  assert(telemetry in customizers)
  assert(telemetry.config.traceHealth)
  assert(telemetry.openTelemetry === openTelemetry)
  telemetry.interceptor
  assert(customizer.customized)
}

private val noopOpenTelemetry: OpenTelemetry = OpenTelemetry.noop()

sealed interface GrpcTelemetryTestScope

@BindingContainer
object GrpcTelemetryTestBindings {
  @Provides fun configBinder(): ConfigBinder = telemetryConfigBinder()
  @Provides fun openTelemetry(): OpenTelemetry = noopOpenTelemetry
  val customizer = RecordingTelemetryCustomizer()
  @Provides @IntoSet fun telemetryCustomizer(): GrpcServerTelemetryCustomizer = customizer
}

@DependencyGraph(
  GrpcTelemetryTestScope::class,
  additionalScopes = [BootScope::class],
  bindingContainers = [GrpcTelemetryTestBindings::class],
)
interface GrpcTelemetryGraph : BootableGraph {
  val grpcTelemetry: GrpcTelemetry
  val grpcServerCustomizers: Set<GrpcServerCustomizer>
}

/** An application's module, composed as an Inara application composes it, with a telemetry customizer of its own. */
@DIModule(composed = true)
interface AppTelemetryModule :
  ConfigModule,
  BootableModule,
  OpenTelemetryApiModule,
  GrpcServerModule,
  GrpcTelemetryModule {
  @GrpcServerTelemetryCustomizers val recordingCustomizer: RecordingTelemetryCustomizer
}

val GrpcTelemetryWiringTest by testSuite {
  test("GrpcTelemetry customizes the server, with its config and customizers, in kodein") {
    val customizer = RecordingTelemetryCustomizer()
    val di = DI {
      import(bootableModule)
      import(grpcServerModule)
      import(grpcTelemetryModule)
      bindSingleton<ConfigBinder> { telemetryConfigBinder() }
      bindSingleton<OpenTelemetry> { noopOpenTelemetry }
      inBindSet<GrpcServerTelemetryCustomizer> { add { singleton { customizer } } }
    }.direct
    checkTelemetry(di.instance(), di.instance(), noopOpenTelemetry, customizer)
  }

  test("GrpcTelemetry customizes the server, with its config and customizers, in koin") {
    val customizer = RecordingTelemetryCustomizer()
    val koin = koinApplication {
      modules(
        koinBootableModule(),
        koinGrpcServerModule(),
        koinGrpcTelemetryModule(),
        module {
          single<ConfigBinder> { telemetryConfigBinder() }
          single<OpenTelemetry> { noopOpenTelemetry }
          single<GrpcServerTelemetryCustomizer>(named("recording")) { customizer }
        },
      )
    }.koin
    try {
      checkTelemetry(koin.get(), koin.getAll<GrpcServerCustomizer>().toSet(), noopOpenTelemetry, customizer)
    } finally {
      koin.close()
    }
  }

  test("GrpcTelemetry customizes the server, with its config and customizers, in metro") {
    val graph = createGraph<GrpcTelemetryGraph>()
    checkTelemetry(graph.grpcTelemetry, graph.grpcServerCustomizers, noopOpenTelemetry, GrpcTelemetryTestBindings.customizer)
  }

  test("GrpcTelemetry customizes the server, with its config and customizers, in inara") {
    val customizer = RecordingTelemetryCustomizer()
    val module = AppTelemetryModule(
      configModule = ConfigModule(configBinder = Binding.value(telemetryConfigBinder())),
      bootableModule = bootableModule(),
      openTelemetryApiModule = NoopOpenTelemetryModule,
      grpcServerModule = grpcServerModule(),
      grpcTelemetryModule = grpcTelemetryModule(),
      recordingCustomizer = Binding.value(customizer),
    )
    checkTelemetry(module.grpcTelemetry, module.setOfGrpcServerCustomizers(), module.openTelemetry, customizer)
  }
}
