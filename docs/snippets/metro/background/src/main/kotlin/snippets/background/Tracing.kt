package snippets.background

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.periodic.PeriodicTracingOptions
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerTracingOptions
import com.github.rocketraman.bootable.telemetry.otel.Sample
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides

// boot-telemetry-otel-metro contributes the graph's OpenTelemetry, so BackgroundGraph traces each loop's runs and each
// task's executions with no code of the application's. These graphs change that tracing.

// tag::periodic-options[]
@BindingContainer
object AlwaysSampledPeriodicTracing {
  @Provides fun periodicTracingOptions(): PeriodicTracingOptions =
    PeriodicTracingOptions(sample = Sample.Always)  // or enabled = false
}

@DependencyGraph(
  BackgroundScope::class,
  additionalScopes = [BootScope::class],
  bindingContainers = [AlwaysSampledPeriodicTracing::class],
)
interface AlwaysSampledPeriodicGraph : BootableGraph
// end::periodic-options[]

// tag::scheduler-options[]
@BindingContainer
object AlwaysSampledSchedulerTracing {
  @Provides fun dbSchedulerTracingOptions(): DbSchedulerTracingOptions =
    DbSchedulerTracingOptions(sample = Sample.Always)  // or enabled = false
}

@DependencyGraph(
  BackgroundScope::class,
  additionalScopes = [BootScope::class],
  bindingContainers = [AlwaysSampledSchedulerTracing::class],
)
interface AlwaysSampledSchedulerGraph : BootableGraph
// end::scheduler-options[]
