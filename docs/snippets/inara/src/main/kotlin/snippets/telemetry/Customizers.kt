package snippets.telemetry

import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizers
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.excludeUrlPathsFromTraces
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

// tag::customizers[]
@DIModule
interface TelemetryCustomizersModule : @DIModule.Required OpenTelemetryModule {
  @OpenTelemetryCustomizers val requestDurationBuckets: OpenTelemetryCustomizer
  @OpenTelemetryCustomizers val pingExclusion: OpenTelemetryCustomizer
}

fun telemetryCustomizersModule() = TelemetryCustomizersModulePart(
  requestDurationBuckets = Binding.value(RequestDurationBuckets),
  pingExclusion = Binding.single { excludeUrlPathsFromTraces(setOf("/ping")) },  // no trace for these requests
)
// end::customizers[]
