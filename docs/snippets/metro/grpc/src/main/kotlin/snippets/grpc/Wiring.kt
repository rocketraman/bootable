package snippets.grpc

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.boot
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import io.grpc.BindableService

sealed interface GrpcScope

// tag::wire[]
// boot-server-grpc-metro contributes the GrpcServer, from grpc.server, with GrpcHealth, from health.grpc, over the
// graph's Set<BindableService>; boot-server-grpc-otel-metro adds GrpcTelemetry to its customizers
@ContributesTo(GrpcScope::class)
interface GreeterBindings {
  @Provides @IntoSet fun greeter(): BindableService = GreeterService()
}
// end::wire[]

// tag::graph[]
@DependencyGraph(GrpcScope::class, additionalScopes = [BootScope::class])
interface GrpcGraph : BootableGraph

fun main() = boot {
  createGraph<GrpcGraph>().bootable
}
// end::graph[]
