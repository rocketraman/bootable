package com.github.rocketraman.bootable.telemetry.otel

import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry

/**
 * A Micrometer [MeterRegistry] that records into [openTelemetry]'s metrics, for libraries that report only to
 * Micrometer, such as the Temporal SDK or Ktor's `MicrometerMetrics`. Their meters are then exported with the rest of
 * the application's metrics, with its resource, by whichever exporters the SDK has.
 *
 * ```
 * val registry = openTelemetryMeterRegistry(telemetry.openTelemetry)
 * ```
 *
 * The meters keep their Micrometer names and tags; there is no Micrometer naming convention or common tags to set,
 * since the resource describes the application. The registry needs no closing: the SDK exports its meters' last
 * values when `OpenTelemetryService` shuts it down.
 */
fun openTelemetryMeterRegistry(openTelemetry: OpenTelemetry): MeterRegistry =
  OpenTelemetryMeterRegistry.builder(openTelemetry).build()
