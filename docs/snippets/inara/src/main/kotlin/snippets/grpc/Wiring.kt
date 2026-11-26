package snippets.grpc

import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.clock.ClockModule
import com.github.rocketraman.bootable.clock.clockModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.server.grpc.GrpcServerModule
import com.github.rocketraman.bootable.server.grpc.GrpcServices
import com.github.rocketraman.bootable.server.grpc.GrpcTelemetryModule
import com.github.rocketraman.bootable.server.grpc.grpcServerModule
import com.github.rocketraman.bootable.server.grpc.grpcTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

// tag::wire[]
@DIModule(composed = true)
interface GrpcAppModule : ConfigModule, BootableModule, GrpcServerModule {
  @GrpcServices val greeterService: GreeterService
}

fun grpcAppModule() = GrpcAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),      // the lifecycle, and its HealthLifecycleController
  grpcServerModule = grpcServerModule(),  // the GrpcServer, from grpc.server, with GrpcHealth, from health.grpc
  greeterService = Binding.single { GreeterService() },
)

fun main() = boot {
  grpcAppModule().bootable
}
// end::wire[]

// tag::telemetry[]
@DIModule(composed = true)
interface TracedGrpcAppModule :
  ConfigModule, BootableModule, ClockModule, OpenTelemetryModule, GrpcServerModule, GrpcTelemetryModule {
  @GrpcServices val greeterService: GreeterService
}

fun tracedGrpcAppModule() = TracedGrpcAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  clockModule = clockModule(),
  openTelemetryModule = openTelemetryModule(),  // the application's OpenTelemetry, from boot-telemetry-otel
  grpcServerModule = grpcServerModule(),
  grpcTelemetryModule = grpcTelemetryModule(),  // GrpcTelemetry, from telemetry.grpc, one of the server's customizers
  greeterService = Binding.single { GreeterService() },
)
// end::telemetry[]
