package com.github.rocketraman.bootable.server.grpc

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.config.config
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Where and how a [GrpcServer] serves. Every field is optional.
 *
 * Decoded from the `grpc.server` config section:
 *
 * ```
 * grpc {
 *   server {
 *     hostPort = ":50051"
 *     shutdownGracePeriod = 10s
 *     reflection = true
 *   }
 * }
 * ```
 */
data class GrpcServerConfig(
  /**
   * Where to listen, such as `:50051`, or `127.0.0.1:0` for any free port. Port [DEFAULT_PORT] if it has none: gRPC's
   * customary port, clear of `:9090`, which Prometheus, and the metrics listeners of Bootable's own modules, often take.
   */
  override val hostPort: String = ":$DEFAULT_PORT",
  /**
   * How long a shutdown waits for the calls in flight to complete, once the server has stopped taking new ones,
   * before it cancels them. A server-streaming call that never ends on its own holds the shutdown this long.
   */
  val shutdownGracePeriod: Duration = DEFAULT_SHUTDOWN_GRACE_PERIOD,
  /** How long a shutdown then waits for the cancelled calls to finish. */
  val shutdownTimeout: Duration = DEFAULT_SHUTDOWN_TIMEOUT,
  /** Whether to serve gRPC server reflection, for tools such as `grpcurl`, which then need no protos. */
  val reflection: Boolean = false,
  /** The largest message the server accepts, in bytes; grpc-java's default, 4 MiB, if unset. */
  val maxInboundMessageSize: Int? = null,
) : HostPort {
  companion object {
    const val DEFAULT_PORT = 50051
    val DEFAULT_SHUTDOWN_GRACE_PERIOD = 10.seconds
    val DEFAULT_SHUTDOWN_TIMEOUT = 2.seconds
  }
}

/** Decodes [GrpcServerConfig] from the `grpc.server` config section using the given [ConfigBinder]. */
fun grpcServerConfig(binder: ConfigBinder): GrpcServerConfig = binder.config("grpc.server")
