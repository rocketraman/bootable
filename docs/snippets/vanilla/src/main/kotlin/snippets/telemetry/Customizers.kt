package snippets.telemetry

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.excludeUrlPathsFromTraces
import com.github.rocketraman.bootable.telemetry.otel.telemetryConfig

fun customizedTelemetry(binder: ConfigBinder): OpenTelemetryService =
  // tag::customizers[]
  OpenTelemetryService(
    telemetryConfig(binder),
    listOf(
      RequestDurationBuckets,
      excludeUrlPathsFromTraces(setOf("/ping")),  // no trace for these requests
    ),
  )
  // end::customizers[]
