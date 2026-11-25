package com.github.rocketraman.bootable.server.grpc

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.opentelemetry.api.OpenTelemetry

/**
 * Provides [GrpcTelemetry], over the OpenTelemetry of `OpenTelemetryBindings` (from boot-telemetry-otel-metro), with
 * its [GrpcTelemetryConfig] decoded from the `telemetry.grpc` config section, and contributes it to the gRPC server's
 * customizers, from boot-server-grpc-metro's `GrpcServerBindings`.
 *
 * To adjust the instrumentation beyond [GrpcTelemetryConfig], contribute a [GrpcServerTelemetryCustomizer].
 */
@ContributesTo(BootScope::class)
interface GrpcTelemetryBindings {
  @Multibinds(allowEmpty = true) val grpcServerTelemetryCustomizers: Set<GrpcServerTelemetryCustomizer>

  companion object {
    @Provides @SingleIn(BootScope::class)
    fun grpcTelemetryConfig(binder: ConfigBinder): GrpcTelemetryConfig = binder.config("telemetry.grpc")

    @Provides @SingleIn(BootScope::class)
    fun grpcTelemetry(
      openTelemetry: OpenTelemetry,
      config: GrpcTelemetryConfig,
      customizers: Set<GrpcServerTelemetryCustomizer>,
    ): GrpcTelemetry = GrpcTelemetry(openTelemetry, config) { builder -> customizers.forEach { it.customize(builder) } }

    @Provides @IntoSet
    fun grpcTelemetryCustomizer(telemetry: GrpcTelemetry): GrpcServerCustomizer = telemetry
  }
}
