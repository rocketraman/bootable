package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.LifecycleController
import org.kodein.di.DI
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.instanceOrNull
import org.kodein.di.singleton
import kotlin.time.Clock

/**
 * A Kodein module that traces the application's start as one trace, with a span for each service's start, by adding
 * [ServiceStartTracing] to the [LifecycleController] set of `bootableModule` (from boot-kodein). Import it alongside
 * [openTelemetryModule], which provides the `OpenTelemetry`. Like that module, it uses the `kotlin.time.Clock` binding,
 * if there is one, or else `Clock.System`.
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val serviceStartTracingModule by DI.Module {
  inBindSet<LifecycleController> { add { singleton { ServiceStartTracing(instance(), instanceOrNull<Clock>() ?: Clock.System) } } }
}
