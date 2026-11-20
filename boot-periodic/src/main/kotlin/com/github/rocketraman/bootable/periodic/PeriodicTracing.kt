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

/**
 * How the Kodein and Metro flavors of this module trace the runs of the application's periodic services. They trace
 * each run with [periodicTracing] whenever the application has an `OpenTelemetry`, such as boot-telemetry-otel's, and
 * don't trace at all when it has none. Bind one to change that, e.g. to sample every run's trace, or to trace none:
 *
 * ```
 * // Kodein
 * bindSingleton { PeriodicTracingOptions(sample = Sample.Always) }
 *
 * // Metro
 * @Provides fun periodicTracingOptions(): PeriodicTracingOptions = PeriodicTracingOptions(enabled = false)
 * ```
 */
data class PeriodicTracingOptions(
  /** Whether to trace the runs, when the application has an `OpenTelemetry`. */
  val enabled: Boolean = true,
  /** How each run's trace is sampled: see [periodicTracing]. */
  val sample: Sample = Sample.BySampler,
) {
  /**
   * The [periodicTracing] interceptor through [openTelemetry], or null when these options turn tracing off, or
   * [openTelemetry] is `OpenTelemetry.noop()`, as the flavors pass when the application has no `OpenTelemetry`: a run
   * that isn't traced then costs nothing for it.
   */
  fun interceptor(openTelemetry: OpenTelemetry): PeriodicRunInterceptor? =
    if (enabled && openTelemetry !== OpenTelemetry.noop()) periodicTracing(openTelemetry, sample) else null
}

private const val INSTRUMENTATION_SCOPE = "com.github.rocketraman.bootable.periodic"
