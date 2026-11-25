@file:JvmName("GrpcServerModuleInara")

package com.github.rocketraman.bootable.server.grpc

import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.BootableModule
import io.grpc.BindableService
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.inara.DISet

/** Marks a module member as one of the [BindableService]s the application's gRPC server serves. */
@DISet(of = BindableService::class)
annotation class GrpcServices

/** Marks a module member as one of the [GrpcServerCustomizer]s of the application's gRPC server. */
@DISet(of = GrpcServerCustomizer::class)
annotation class GrpcServerCustomizers

/**
 * The Inara module that adds a [GrpcServer] to the app services, with its [GrpcServerConfig] decoded from the
 * `grpc.server` config section, serving every member marked [GrpcServices], and the standard health service, from the
 * `HealthLifecycleController` of the `BootableModule` it requires, as [GrpcHealth], with its [GrpcHealthConfig]
 * decoded from the `health.grpc` config section.
 *
 * Mark the application's services [GrpcServices]:
 *
 * ```
 * @GrpcServices val greeterService: GreeterService
 * ```
 *
 * Adjust the server with a member marked [GrpcServerCustomizers], as boot-server-grpc-otel-inara's
 * `grpcTelemetryModule()` does.
 */
@DIModule(sets = [GrpcServices::class, GrpcServerCustomizers::class])
interface GrpcServerModule : @DIModule.Required BootableModule {
  val grpcServerConfig: GrpcServerConfig
  val grpcHealth: GrpcHealth
  @AppServices val grpcServer: GrpcServer
}

/** A [GrpcServerModule]. */
fun grpcServerModule(): GrpcServerModulePart = GrpcServerModulePart(
  // qualified: the grpcServerConfig member shadows the top-level function
  grpcServerConfig = Binding.single { com.github.rocketraman.bootable.server.grpc.grpcServerConfig(configBinder) },
  grpcHealth = Binding.single { GrpcHealth(health, grpcHealthConfig(configBinder)) },
  grpcServer = Binding.single {
    GrpcServer(grpcServerConfig, setOfGrpcServices(), grpcHealth, setOfGrpcServerCustomizers())
  },
)
