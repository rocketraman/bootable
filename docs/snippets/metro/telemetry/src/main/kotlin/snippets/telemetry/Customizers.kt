package snippets.telemetry

import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.excludeUrlPathsFromTraces
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@ContributesTo(TelemetryScope::class)
interface TelemetryCustomizerBindings {
  // tag::customizers[]
  @Provides @IntoSet fun requestDurationBuckets(): OpenTelemetryCustomizer = RequestDurationBuckets

  @Provides @IntoSet  // no trace for these requests
  fun excludePing(): OpenTelemetryCustomizer = excludeUrlPathsFromTraces(setOf("/ping"))
  // end::customizers[]
}
