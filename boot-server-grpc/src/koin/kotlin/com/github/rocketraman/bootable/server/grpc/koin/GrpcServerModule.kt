package com.github.rocketraman.bootable.server.grpc.koin

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.server.grpc.GrpcHealth
import com.github.rocketraman.bootable.server.grpc.GrpcHealthConfig
import com.github.rocketraman.bootable.server.grpc.GrpcServerConfig
import com.github.rocketraman.bootable.server.grpc.GrpcServerCustomizer
import com.github.rocketraman.bootable.server.grpc.GrpcServer
import com.github.rocketraman.bootable.server.grpc.grpcHealthConfig
import com.github.rocketraman.bootable.server.grpc.grpcServerConfig
import io.grpc.BindableService
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that adds a [GrpcServer] to the app services, with its [GrpcServerConfig] decoded from the
 * `grpc.server` config section, serving every [BindableService] the application defines, and the standard health
 * service, from the `HealthLifecycleController` of `bootableModule()` (from boot-koin), as [GrpcHealth], with its
 * [GrpcHealthConfig] decoded from the `health.grpc` config section. Requires a `ConfigBinder` definition (e.g. from
 * boot-config-hoplite's `hopliteConfigModule()`).
 *
 * Add a service with a qualifier of its own: `single<BindableService>(named("greeter")) { GreeterService() }`. Adjust
 * the server with a [GrpcServerCustomizer], defined the same way, such as boot-server-grpc-otel-koin's.
 */
fun grpcServerModule(): Module = module {
  single { grpcServerConfig(get()) }
  single { GrpcHealth(get(), grpcHealthConfig(get())) }
  single<AppService>(named("grpcServer")) {
    GrpcServer(get(), getAll<BindableService>().toSet(), get(), getAll<GrpcServerCustomizer>().toSet())
  }
}
