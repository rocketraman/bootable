package com.github.rocketraman.bootable.server.grpc

import com.github.rocketraman.bootable.config.bindConfig
import org.kodein.di.DI
import org.kodein.di.bindSet
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that provides [GrpcTelemetry], over the OpenTelemetry of `openTelemetryModule` (from
 * boot-telemetry-otel-kodein), with its [GrpcTelemetryConfig] decoded from the `telemetry.grpc` config section, and
 * adds it to the gRPC server's customizers, from boot-server-grpc-kodein's `grpcServerModule`.
 *
 * To adjust the instrumentation beyond [GrpcTelemetryConfig], add a [GrpcServerTelemetryCustomizer] to the
 * `Set<GrpcServerTelemetryCustomizer>` it opens.
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val grpcTelemetryModule by DI.Module {
  bindSet<GrpcServerTelemetryCustomizer>()
  bindConfig<GrpcTelemetryConfig>("telemetry.grpc")
  bindSingleton {
    val customizers = instance<Set<GrpcServerTelemetryCustomizer>>()
    GrpcTelemetry(instance(), instance()) { builder -> customizers.forEach { it.customize(builder) } }
  }
  inBindSet<GrpcServerCustomizer> { add { singleton { instance<GrpcTelemetry>() } } }
}
