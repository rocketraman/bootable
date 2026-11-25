import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.defaultLifecycleControllers
import com.github.rocketraman.bootable.boot.readinessConfig
import com.github.rocketraman.bootable.boot.withHealthLifecycleController
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.example.grpc.GreeterGrpcKt
import com.github.rocketraman.bootable.example.grpc.HelloReply
import com.github.rocketraman.bootable.example.grpc.HelloRequest
import com.github.rocketraman.bootable.example.grpc.helloReply
import com.github.rocketraman.bootable.server.grpc.GrpcHealth
import com.github.rocketraman.bootable.server.grpc.GrpcServer
import com.github.rocketraman.bootable.server.grpc.grpcHealthConfig
import com.github.rocketraman.bootable.server.grpc.grpcServerConfig
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.apache.logging.log4j.kotlin.logger

data class GreeterConfig(val greeting: String = "Hello")

/**
 * An ordinary class, on the server base kotpb-grpc-codegen generates from greeter.proto: each method is a coroutine,
 * checked against the proto by the compiler, and its replies are built with protoc's own Kotlin builders.
 */
class GreeterService(private val config: GreeterConfig) : GreeterGrpcKt.GreeterCoroutineImplBase() {
  private val log = logger()

  override suspend fun sayHello(request: HelloRequest): HelloReply {
    log.info { "SayHello ${request.name}" }
    return helloReply { message = "${config.greeting}, ${request.name}" }
  }

  override fun streamHellos(request: HelloRequest): Flow<HelloReply> = flow {
    repeat(request.count) {
      delay(100.milliseconds)
      emit(helloReply { message = "${config.greeting} #${it + 1}, ${request.name}" })
    }
  }
}

/** The application's services, from its config, and its health, which the gRPC health service answers from. */
fun appServices(binder: com.github.rocketraman.bootable.config.ConfigBinder, health: HealthLifecycleController) = setOf(
  GrpcServer(
    grpcServerConfig(binder),
    setOf(GreeterService(binder.config("greeter"))),
    GrpcHealth(health, grpcHealthConfig(binder)),
  ),
)

/** Vanilla wiring: plain Kotlin, no DI framework. */
fun main() = boot {
  val binder = hopliteConfigBinder()
  val health = HealthLifecycleController(config = readinessConfig(binder))
  Bootable(appServices(binder, health), defaultLifecycleControllers().withHealthLifecycleController(health))
}
