package com.github.rocketraman.bootable.scheduler.db

import com.github.rocketraman.bootable.telemetry.otel.Sample
import org.kodein.di.DI
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that traces each execution of the application's db-scheduler tasks through its OpenTelemetry, by
 * adding [dbSchedulerTracing] to the [DbSchedulerCustomizer] set of `dbSchedulerModule` (from boot-scheduler-db-kodein).
 * Import it after `dbSchedulerModule`, which opens that set, alongside `openTelemetryModule` (from
 * boot-telemetry-otel-kodein), which provides the `OpenTelemetry`.
 */
fun dbSchedulerTracingModule(sample: Sample = Sample.BySampler): DI.Module = DI.Module("dbSchedulerTracingModule") {
  inBindSet<DbSchedulerCustomizer> { add { singleton { dbSchedulerTracing(instance(), sample) } } }
}
