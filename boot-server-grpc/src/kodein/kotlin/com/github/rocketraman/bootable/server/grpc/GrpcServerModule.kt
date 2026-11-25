package com.github.rocketraman.bootable.server.grpc

import com.github.rocketraman.bootable.boot.bindAppService
import io.grpc.BindableService
import org.kodein.di.DI
import org.kodein.di.bindSet
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that adds a [GrpcServer] to the app services, with its [GrpcServerConfig] decoded from the
 * `grpc.server` config section, serving the `Set<BindableService>` it opens, and the standard health service, from
 * the `HealthLifecycleController` of `bootableModule` (from boot-kodein), as [GrpcHealth], with its
 * [GrpcHealthConfig] decoded from the `health.grpc` config section. Requires a `ConfigBinder` binding (e.g. from
 * boot-config-hoplite's `hopliteConfigModule`).
 *
 * Add a service with [bindGrpcService], and adjust the server with a [GrpcServerCustomizer], in the
 * `Set<GrpcServerCustomizer>` it opens, such as boot-server-grpc-otel-kodein's.
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val grpcServerModule by DI.Module {
  bindSet<BindableService>()
  bindSet<GrpcServerCustomizer>()
  bindSingleton { grpcServerConfig(instance()) }
  bindSingleton { GrpcHealth(instance(), grpcHealthConfig(instance())) }
  bindAppService { singleton { GrpcServer(instance(), instance(), instance(), instance()) } }
}

/** Contribute a service to the gRPC server: `bindGrpcService { singleton { GreeterService() } }`. */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
fun DI.Builder.bindGrpcService(createBinding: () -> org.kodein.di.bindings.DIBinding<*, *, out BindableService>) =
  inBindSet<BindableService> { add(createBinding) }
