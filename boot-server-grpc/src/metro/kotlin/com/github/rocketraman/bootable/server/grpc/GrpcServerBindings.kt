package com.github.rocketraman.bootable.server.grpc

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.config.ConfigBinder
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.grpc.BindableService

/**
 * Contributes a [GrpcServer] to the app services, with its [GrpcServerConfig] decoded from the `grpc.server` config
 * section, serving the graph's `Set<BindableService>`, and the standard health service, from the
 * `HealthLifecycleController` of `BootableDefaultBindings` (from boot-metro), as [GrpcHealth], with its
 * [GrpcHealthConfig] decoded from the `health.grpc` config section.
 *
 * Add a service with `@Provides @IntoSet fun greeter(): BindableService = GreeterService()`, and adjust the server
 * with an `@IntoSet` [GrpcServerCustomizer], such as boot-server-grpc-otel-metro's.
 */
@ContributesTo(BootScope::class)
interface GrpcServerBindings {
  @Multibinds(allowEmpty = true) val grpcServices: Set<BindableService>
  @Multibinds(allowEmpty = true) val grpcServerCustomizers: Set<GrpcServerCustomizer>

  companion object {
    @Provides @SingleIn(BootScope::class)
    fun grpcServerConfig(binder: ConfigBinder): GrpcServerConfig =
      com.github.rocketraman.bootable.server.grpc.grpcServerConfig(binder)

    @Provides @SingleIn(BootScope::class)
    fun grpcHealth(health: HealthLifecycleController, binder: ConfigBinder): GrpcHealth =
      GrpcHealth(health, grpcHealthConfig(binder))

    @Provides @IntoSet @SingleIn(BootScope::class)
    fun grpcServer(
      config: GrpcServerConfig,
      services: Set<BindableService>,
      health: GrpcHealth,
      customizers: Set<GrpcServerCustomizer>,
    ): AppService = GrpcServer(config, services, health, customizers)
  }
}
