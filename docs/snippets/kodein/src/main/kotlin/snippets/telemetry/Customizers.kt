package snippets.telemetry

import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.excludeUrlPathsFromTraces
import org.kodein.di.DI
import org.kodein.di.inBindSet
import org.kodein.di.singleton

/** Imported after openTelemetryModule, which opens the set. */
val telemetryCustomizersModule by DI.Module {
  // tag::customizers[]
  inBindSet<OpenTelemetryCustomizer> {
    add { singleton { RequestDurationBuckets } }
    add { singleton { excludeUrlPathsFromTraces(setOf("/ping")) } }  // no trace for these requests
  }
  // end::customizers[]
}
