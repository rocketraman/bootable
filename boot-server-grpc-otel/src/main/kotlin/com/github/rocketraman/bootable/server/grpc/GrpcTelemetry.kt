package com.github.rocketraman.bootable.server.grpc

import com.github.rocketraman.bootable.server.grpc.health.HealthGrpcKt
import io.grpc.Metadata
import io.grpc.ServerBuilder
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetryBuilder
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry as OtelGrpcTelemetry

/**
 * How a gRPC server reports its telemetry. Every field is optional.
 *
 * Decoded from the `telemetry.grpc` config section:
 *
 * ```
 * telemetry {
 *   grpc {
 *     capturedRequestMetadata = [x-request-id]
 *   }
 * }
 * ```
 */
data class GrpcTelemetryConfig(
  /** Request metadata keys to record on each call's span, as `rpc.grpc.request.metadata.<key>`. */
  val capturedRequestMetadata: List<String> = emptyList(),
  /** Whether to trace the health service's calls too, which the platform's probes would otherwise fill the traces with. */
  val traceHealth: Boolean = false,
)

/**
 * Adjusts OpenTelemetry's gRPC instrumentation after the typed settings are applied, for settings
 * [GrpcTelemetryConfig] doesn't cover, such as extra span attributes.
 */
fun interface GrpcServerTelemetryCustomizer {
  fun customize(builder: GrpcTelemetryBuilder)
}

/**
 * A gRPC server's telemetry, as a [GrpcServerCustomizer]: OpenTelemetry's gRPC instrumentation, which traces every
 * call, continuing the caller's trace when the call carries one, and records the standard RPC server metrics, such
 * as `rpc.server.duration`.
 *
 * The call's span is current in its handler's coroutine, so spans started there, e.g. with `Tracer.withSpan`, are its
 * children, and log events carry its trace id.
 *
 * Add it to [GrpcServer]'s customizers; this module's DI flavors add it for you.
 */
class GrpcTelemetry(
  val openTelemetry: OpenTelemetry,
  val config: GrpcTelemetryConfig = GrpcTelemetryConfig(),
  private val customizer: GrpcServerTelemetryCustomizer = GrpcServerTelemetryCustomizer { },
) : GrpcServerCustomizer {

  /** OpenTelemetry's server interceptor, which skips the health service, unless [GrpcTelemetryConfig.traceHealth]. */
  val interceptor: ServerInterceptor by lazy {
    val telemetry = OtelGrpcTelemetry.builder(openTelemetry)
      .setCapturedServerRequestMetadata(config.capturedRequestMetadata)
      .also { customizer.customize(it) }
      .build()
      .createServerInterceptor()
    if (config.traceHealth) telemetry else SkipHealth(telemetry)
  }

  override fun customize(builder: ServerBuilder<*>) {
    builder.intercept(interceptor)
  }

  private class SkipHealth(private val delegate: ServerInterceptor) : ServerInterceptor {
    override fun <Req, Resp> interceptCall(
      call: ServerCall<Req, Resp>,
      headers: Metadata,
      next: ServerCallHandler<Req, Resp>,
    ): ServerCall.Listener<Req> =
      if (call.methodDescriptor.serviceName == HealthGrpcKt.SERVICE_NAME) next.startCall(call, headers)
      else delegate.interceptCall(call, headers, next)
  }
}
