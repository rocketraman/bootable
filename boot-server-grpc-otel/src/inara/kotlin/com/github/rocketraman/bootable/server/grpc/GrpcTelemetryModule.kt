@file:JvmName("GrpcTelemetryModuleInara")

package com.github.rocketraman.bootable.server.grpc

import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryApiModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.inara.DISet

/** Marks a module member as one of the [GrpcServerTelemetryCustomizer]s of the gRPC server instrumentation. */
@DISet(of = GrpcServerTelemetryCustomizer::class)
annotation class GrpcServerTelemetryCustomizers

/**
 * The Inara module that provides [GrpcTelemetry], over the OpenTelemetry of the [OpenTelemetryApiModule] it requires,
 * such as `openTelemetryModule()` from boot-telemetry-otel-inara, with its [GrpcTelemetryConfig] decoded from the
 * `telemetry.grpc` config section of the [ConfigModule] it requires. It is one of the [GrpcServerCustomizers] of
 * boot-server-grpc-inara's `grpcServerModule()`.
 *
 * To adjust the instrumentation beyond [GrpcTelemetryConfig], mark a member [GrpcServerTelemetryCustomizers].
 */
@DIModule(sets = [GrpcServerTelemetryCustomizers::class])
interface GrpcTelemetryModule : @DIModule.Required OpenTelemetryApiModule, @DIModule.Required ConfigModule {
  val grpcTelemetryConfig: GrpcTelemetryConfig
  @GrpcServerCustomizers val grpcTelemetry: GrpcTelemetry
}

/** A [GrpcTelemetryModule]. */
fun grpcTelemetryModule(): GrpcTelemetryModulePart = GrpcTelemetryModulePart(
  grpcTelemetryConfig = config("telemetry.grpc"),
  grpcTelemetry = Binding.single {
    val customizers = setOfGrpcServerTelemetryCustomizers()
    GrpcTelemetry(openTelemetry, grpcTelemetryConfig) { builder -> customizers.forEach { it.customize(builder) } }
  },
)
