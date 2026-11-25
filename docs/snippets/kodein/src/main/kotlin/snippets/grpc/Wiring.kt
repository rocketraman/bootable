@file:Suppress("DEPRECATION")

package snippets.grpc

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.server.grpc.bindGrpcService
import com.github.rocketraman.bootable.server.grpc.grpcServerModule
import com.github.rocketraman.bootable.server.grpc.grpcTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.di.singleton

// tag::wire[]
val greeterModule by DI.Module {
  import(grpcServerModule)  // the GrpcServer, from grpc.server, with GrpcHealth, from health.grpc
  bindGrpcService { singleton { GreeterService() } }
}

fun grpcDI() = DI {
  import(bootableModule)  // the lifecycle, and its HealthLifecycleController
  import(hopliteConfigModule)
  import(greeterModule)
}

fun main() = boot {
  grpcDI().direct.instance<Bootable>()
}
// end::wire[]

fun tracedGrpcDI() = DI {
  // tag::telemetry[]
  import(bootableModule)
  import(hopliteConfigModule)
  import(openTelemetryModule)  // the application's OpenTelemetry, from boot-telemetry-otel
  import(greeterModule)
  import(grpcTelemetryModule)  // GrpcTelemetry, from telemetry.grpc, one of the server's customizers
  // end::telemetry[]
}
