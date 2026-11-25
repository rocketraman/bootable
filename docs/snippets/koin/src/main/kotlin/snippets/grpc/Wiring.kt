package snippets.grpc

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import com.github.rocketraman.bootable.server.grpc.koin.grpcServerModule
import com.github.rocketraman.bootable.server.grpc.koin.grpcTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule
import io.grpc.BindableService
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

// tag::wire[]
val greeterModule = module {
  includes(grpcServerModule())  // the GrpcServer, from grpc.server, with GrpcHealth, from health.grpc
  single<BindableService>(named<GreeterService>()) { GreeterService() }
}

fun grpcKoin() = koinApplication {
  modules(
    bootableModule(),  // the lifecycle, and its HealthLifecycleController
    hopliteConfigModule(),
    greeterModule,
  )
}.koin

fun main() = boot {
  grpcKoin().get<Bootable>()
}
// end::wire[]

fun tracedGrpcKoin() = koinApplication {
  modules(
    // tag::telemetry[]
    bootableModule(),
    hopliteConfigModule(),
    openTelemetryModule(),  // the application's OpenTelemetry, from boot-telemetry-otel
    greeterModule,
    grpcTelemetryModule(),  // GrpcTelemetry, from telemetry.grpc, one of the server's customizers
    // end::telemetry[]
  )
}.koin
