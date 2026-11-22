package snippets.telemetry

import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import io.opentelemetry.sdk.metrics.Aggregation
import io.opentelemetry.sdk.metrics.InstrumentSelector
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.metrics.View

// tag::customizer[]
/** Buckets of its own for the server's request durations, in seconds: a metric view, which the config doesn't cover. */
object RequestDurationBuckets : OpenTelemetryCustomizer {
  override fun customizeMeterProvider(builder: SdkMeterProviderBuilder) {
    builder.registerView(
      InstrumentSelector.builder().setName("http.server.request.duration").build(),
      View.builder()
        .setAggregation(Aggregation.explicitBucketHistogram(listOf(0.01, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5)))
        .build(),
    )
  }
}
// end::customizer[]
