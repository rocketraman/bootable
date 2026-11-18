package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.telemetry.otel.Sample
import com.github.rocketraman.bootable.telemetry.otel.withRootSpan
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey

/** The periodic service a run's span belongs to. */
val PERIODIC_SERVICE: AttributeKey<String> = AttributeKey.stringKey("bootable.periodic.service")

/**
 * A [PeriodicRunInterceptor] that traces each run of a [PeriodicAppService] through [openTelemetry]: each run starts a
 * trace of its own, with a root span named for the service, such as `periodic notifications`, so that the spans within
 * it, such as its JDBC statements, and its log events, belong to it. A run that fails ends its span with an error
 * status and the exception recorded.
 *
 * Give it to the service, or to [PeriodicServices]:
 *
 * ```
 * PeriodicServices(interceptors = listOf(periodicTracing(telemetry.openTelemetry)))
 * ```
 *
 * [sample] is whether each run's trace is sampled as the SDK's sampler decides, the default, or always, for loops that
 * run rarely enough that every run's trace is worth keeping.
 */
fun periodicTracing(openTelemetry: OpenTelemetry, sample: Sample = Sample.BySampler): PeriodicRunInterceptor {
  val tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE)
  return object : PeriodicRunInterceptor {
    override suspend fun intercept(service: String, run: suspend () -> Unit) {
      tracer.withRootSpan("periodic $service", sample = sample) { span ->
        span.setAttribute(PERIODIC_SERVICE, service)
        run()
      }
    }

    override fun toString() = "periodicTracing"
  }
}

private const val INSTRUMENTATION_SCOPE = "com.github.rocketraman.bootable.periodic"
