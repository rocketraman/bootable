import com.github.rocketraman.bootable.boot.AppStartStopLifecycleController
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.LifecycleState
import com.github.rocketraman.bootable.example.grpc.GreeterGrpcKt
import com.github.rocketraman.bootable.example.grpc.helloRequest
import com.github.rocketraman.bootable.server.grpc.GrpcHealthConfig
import com.github.rocketraman.bootable.server.grpc.GrpcServer
import com.github.rocketraman.bootable.server.grpc.GrpcServerConfig
import com.github.rocketraman.bootable.server.grpc.health.HealthCheckResponse.ServingStatus
import com.github.rocketraman.bootable.server.grpc.health.HealthGrpcKt
import com.github.rocketraman.bootable.server.grpc.health.healthCheckRequest
import com.github.rocketraman.bootable.test.StaticConfigBinder
import de.infix.testBalloon.framework.core.testSuite
import io.grpc.ManagedChannelBuilder
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

val ExampleGrpcServerTest by testSuite {
  test("boots the application, and serves the greeter and its health") {
    withContext(Dispatchers.Default) {
      val binder = StaticConfigBinder(
        "grpc.server" to GrpcServerConfig(hostPort = "127.0.0.1:0"),
        "health.grpc" to GrpcHealthConfig(),
        "greeter" to GreeterConfig(greeting = "Hi"),
      )
      val health = HealthLifecycleController()
      val services = appServices(binder, health)
      val bootable = Bootable(services, setOf(AppStartStopLifecycleController(), health))
      val run = async { bootable.run() }
      withTimeout(10.seconds) { health.states.first { it == LifecycleState.Running } }

      val channel = ManagedChannelBuilder.forAddress("127.0.0.1", services.single().port).usePlaintext().build()
      try {
        val greeter = GreeterGrpcKt.GreeterCoroutineStub(channel)
        assert(greeter.sayHello(helloRequest { name = "test" }).message == "Hi, test")
        val replies = greeter.streamHellos(helloRequest { name = "test"; count = 2 }).map { it.message }.toList()
        assert(replies == listOf("Hi #1, test", "Hi #2, test"))
        val status = HealthGrpcKt.HealthCoroutineStub(channel).check(healthCheckRequest { service = "readiness" })
        assert(status.status == ServingStatus.SERVING)
      } finally {
        channel.shutdownNow()
        bootable.stop()
        run.await()
      }
    }
  }
}
