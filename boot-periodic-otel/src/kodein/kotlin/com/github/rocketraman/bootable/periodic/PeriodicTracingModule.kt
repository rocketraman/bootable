package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.telemetry.otel.Sample
import org.kodein.di.DI
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that traces each run of the application's periodic services through its OpenTelemetry, by adding
 * [periodicTracing] to the [PeriodicRunInterceptor] set of `periodicModule` (from boot-periodic-kodein). Import it
 * after `periodicModule`, which opens that set, alongside `openTelemetryModule` (from boot-telemetry-otel-kodein),
 * which provides the `OpenTelemetry`.
 */
fun periodicTracingModule(sample: Sample = Sample.BySampler): DI.Module = DI.Module("periodicTracingModule") {
  inBindSet<PeriodicRunInterceptor> { add { singleton { periodicTracing(instance(), sample) } } }
}
