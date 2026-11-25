package com.github.rocketraman.bootable.server.grpc.koin

import com.github.rocketraman.bootable.config.koin.config
import com.github.rocketraman.bootable.server.grpc.GrpcServerCustomizer
import com.github.rocketraman.bootable.server.grpc.GrpcServerTelemetryCustomizer
import com.github.rocketraman.bootable.server.grpc.GrpcTelemetry
import com.github.rocketraman.bootable.server.grpc.GrpcTelemetryConfig
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that provides [GrpcTelemetry], over the OpenTelemetry of `openTelemetryModule()` (from
 * boot-telemetry-otel-koin), with its [GrpcTelemetryConfig] decoded from the `telemetry.grpc` config section, and
 * adds it to the customizers of the gRPC server of boot-server-grpc-koin's `grpcServerModule()`. Requires a
 * `ConfigBinder` definition (e.g. from boot-config-hoplite's `hopliteConfigModule()`).
 *
 * To adjust the instrumentation beyond [GrpcTelemetryConfig], define a [GrpcServerTelemetryCustomizer], with a
 * qualifier of its own.
 */
fun grpcTelemetryModule(): Module = module {
  config<GrpcTelemetryConfig>("telemetry.grpc")
  single {
    val customizers = getAll<GrpcServerTelemetryCustomizer>()
    GrpcTelemetry(get(), get()) { builder -> customizers.forEach { it.customize(builder) } }
  }
  single<GrpcServerCustomizer>(named<GrpcTelemetry>()) { get<GrpcTelemetry>() }
}
