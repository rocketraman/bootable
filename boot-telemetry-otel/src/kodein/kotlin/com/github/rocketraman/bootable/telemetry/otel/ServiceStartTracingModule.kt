package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.LifecycleController
import org.kodein.di.DI
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that traces the application's start as one trace, with a span for each service's start, by adding
 * [ServiceStartTracing] to the [LifecycleController] set of `bootableModule` (from boot-kodein). Import it alongside
 * [openTelemetryModule], which provides the `OpenTelemetry`.
 */
val serviceStartTracingModule by DI.Module {
  inBindSet<LifecycleController> { add { singleton { ServiceStartTracing(instance()) } } }
}
