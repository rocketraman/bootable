package com.github.rocketraman.bootable.server.grpc

import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.LifecycleState
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.server.grpc.health.HealthCheckRequest
import com.github.rocketraman.bootable.server.grpc.health.HealthCheckResponse
import com.github.rocketraman.bootable.server.grpc.health.HealthCheckResponse.ServingStatus
import com.github.rocketraman.bootable.server.grpc.health.HealthGrpcKt
import com.github.rocketraman.bootable.server.grpc.health.healthCheckResponse
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.StatusException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How the standard gRPC health service answers, from the application's [HealthLifecycleController]. Every field is
 * optional.
 *
 * Decoded from the `health.grpc` config section:
 *
 * ```
 * health {
 *   grpc {
 *     watchInterval = 2s
 *   }
 * }
 * ```
 */
data class GrpcHealthConfig(
  /** The service name whose status is the application's liveness. */
  val livenessService: String = DEFAULT_LIVENESS_SERVICE,
  /** The service name whose status is the application's readiness, as is the server's own, the empty name's. */
  val readinessService: String = DEFAULT_READINESS_SERVICE,
  /** How often a `Watch` call checks for a change of status, besides each change of the lifecycle's state. */
  val watchInterval: Duration = 5.seconds,
) {
  companion object {
    const val DEFAULT_LIVENESS_SERVICE = "liveness"
    const val DEFAULT_READINESS_SERVICE = "readiness"
  }
}

/** Decodes [GrpcHealthConfig] from the `health.grpc` config section using the given [ConfigBinder]. */
fun grpcHealthConfig(binder: ConfigBinder): GrpcHealthConfig = binder.config("health.grpc")

/**
 * The standard gRPC health service, `grpc.health.v1.Health`, answered from the application's [health], so that a
 * Kubernetes gRPC probe, a load balancer, or a client's own health checking sees what Bootable's HTTP health routes
 * would:
 *
 * - the empty service name, the server's own, and [GrpcHealthConfig.readinessService], are the application's
 *   readiness, with its checks: `SERVING` while the application is running and every check is healthy;
 * - [GrpcHealthConfig.livenessService] is its liveness: `SERVING` until it has stopped;
 * - each of the server's own services is its readiness too, since they serve only while the application is ready;
 * - any other name is `NOT_FOUND`, or, for `Watch`, `SERVICE_UNKNOWN`.
 *
 * For a Kubernetes probe, name the service:
 *
 * ```
 * livenessProbe:
 *   grpc: { port: 50051, service: liveness }
 * readinessProbe:
 *   grpc: { port: 50051, service: readiness }
 * ```
 *
 * A `Watch` call sends the status, and then each change, until shutdown begins: then it sends the status once more,
 * and completes, so a watcher doesn't hold the server's graceful shutdown.
 *
 * Its server base, `HealthGrpcKt.HealthCoroutineImplBase`, is generated as an application's are: by kotpb-grpc-codegen,
 * from grpc-services' `health.proto`, with its Java package moved to this module's, so its classes don't clash with
 * grpc-services' Java ones for the same proto. The service is the same on the wire.
 *
 * [GrpcServer] serves it, given one.
 */
class GrpcHealth(
  val health: HealthLifecycleController,
  val config: GrpcHealthConfig = GrpcHealthConfig(),
) {
  /** The health service, for a server that serves [serviceNames]. */
  fun bindService(serviceNames: Set<String>): ServerServiceDefinition = HealthService(serviceNames).bindService()

  private inner class HealthService(serviceNames: Set<String>) : HealthGrpcKt.HealthCoroutineImplBase() {
    private val readinessNames = serviceNames + "" + config.readinessService

    private fun probe(service: String): (suspend () -> Boolean)? = when (service) {
      config.livenessService -> { -> health.liveness().healthy }
      in readinessNames -> { -> health.readiness().healthy }
      else -> null
    }

    override suspend fun check(request: HealthCheckRequest): HealthCheckResponse {
      val probe = probe(request.service)
        ?: throw StatusException(Status.NOT_FOUND.withDescription("Unknown service ${request.service}"))
      return response(probe())
    }

    override fun watch(request: HealthCheckRequest): Flow<HealthCheckResponse> = flow {
      val probe = probe(request.service)
      if (probe == null) {
        emit(healthCheckResponse { status = ServingStatus.SERVICE_UNKNOWN })
        awaitCancellation()
      }
      var last: Boolean? = null
      while (true) {
        val state = health.state
        val healthy = probe()
        if (healthy != last) emit(response(healthy))
        last = healthy
        if (state >= LifecycleState.Stopping) return@flow
        withTimeoutOrNull(config.watchInterval) { health.states.first { it != state } }
      }
    }

    private fun response(healthy: Boolean): HealthCheckResponse =
      healthCheckResponse { status = if (healthy) ServingStatus.SERVING else ServingStatus.NOT_SERVING }
  }
}
