package snippets.telemetry

import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.excludeUrlPathsFromTraces
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Included alongside openTelemetryModule(), which collects every OpenTelemetryCustomizer definition. */
val telemetryCustomizersModule = module {
  // tag::customizers[]
  single<OpenTelemetryCustomizer>(named<RequestDurationBuckets>()) { RequestDurationBuckets }
  single<OpenTelemetryCustomizer>(named("ping")) { excludeUrlPathsFromTraces(setOf("/ping")) }  // no trace for these requests
  // end::customizers[]
}
