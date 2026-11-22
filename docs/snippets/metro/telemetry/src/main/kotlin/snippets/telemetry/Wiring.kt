package snippets.telemetry

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.telemetry.otel.ServiceStartTracingBindings
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph

sealed interface TelemetryScope

// tag::wire[]
// boot-telemetry-otel-metro contributes the SDK, from telemetry.sdk, as an OpenTelemetry binding; the start's tracing
// is named here, as a binding container
@DependencyGraph(
  TelemetryScope::class,
  additionalScopes = [BootScope::class],
  bindingContainers = [ServiceStartTracingBindings::class],
)
interface TelemetryGraph : BootableGraph

fun main() = boot {
  createGraph<TelemetryGraph>().bootable
}
// end::wire[]
