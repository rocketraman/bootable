package snippets.grpc

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.defaultLifecycleControllers
import com.github.rocketraman.bootable.boot.readinessConfig
import com.github.rocketraman.bootable.boot.withHealthLifecycleController
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.server.grpc.GrpcHealth
import com.github.rocketraman.bootable.server.grpc.GrpcServer
import com.github.rocketraman.bootable.server.grpc.GrpcServerCustomizer
import com.github.rocketraman.bootable.server.grpc.GrpcTelemetry
import com.github.rocketraman.bootable.server.grpc.grpcHealthConfig
import com.github.rocketraman.bootable.server.grpc.grpcServerConfig
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.telemetryConfig
import java.util.concurrent.TimeUnit

fun grpcApp(binder: ConfigBinder): Bootable {
  // tag::wire[]
  val health = HealthLifecycleController(config = readinessConfig(binder))  // the lifecycle's health
  return Bootable(
    setOf(
      GrpcServer(
        grpcServerConfig(binder),                       // from grpc.server
        setOf(GreeterService()),                        // the services it serves
        GrpcHealth(health, grpcHealthConfig(binder)),   // grpc.health.v1.Health, from health.grpc
      ),
    ),
    defaultLifecycleControllers().withHealthLifecycleController(health),
  )
  // end::wire[]
}

// tag::customizer[]
val keepAlive = GrpcServerCustomizer { builder -> builder.keepAliveTime(30, TimeUnit.SECONDS) }
// end::customizer[]

fun tracedGrpcApp(binder: ConfigBinder): Bootable {
  val health = HealthLifecycleController(config = readinessConfig(binder))
  // tag::telemetry[]
  val telemetry = OpenTelemetryService(telemetryConfig(binder))  // the application's OpenTelemetry
  val grpcTelemetry = GrpcTelemetry(telemetry.openTelemetry, binder.config("telemetry.grpc"))
  return Bootable(
    setOf(
      telemetry,
      GrpcServer(
        grpcServerConfig(binder),
        setOf(GreeterService()),
        GrpcHealth(health, grpcHealthConfig(binder)),
        customizers = setOf(grpcTelemetry),
      ),
    ),
    defaultLifecycleControllers().withHealthLifecycleController(health),
  )
  // end::telemetry[]
}

fun main() = boot { grpcApp(hopliteConfigBinder()) }
