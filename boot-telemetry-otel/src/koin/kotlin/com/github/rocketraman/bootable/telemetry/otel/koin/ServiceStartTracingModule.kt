package com.github.rocketraman.bootable.telemetry.otel.koin

import com.github.rocketraman.bootable.boot.LifecycleController
import com.github.rocketraman.bootable.telemetry.otel.ServiceStartTracing
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * A Koin module that traces the application's start as one trace, with a span for each service's start, by adding
 * [ServiceStartTracing] to the [LifecycleController]s of `bootableModule()` (from boot-koin). Include it alongside
 * [openTelemetryModule], which provides the `OpenTelemetry`. Like that module, it uses the `kotlin.time.Clock`
 * definition, if there is one, or else `Clock.System`.
 */
fun serviceStartTracingModule(): Module = module {
  single<LifecycleController>(named<ServiceStartTracing>()) {
    ServiceStartTracing(get(), getOrNull<Clock>() ?: Clock.System)
  }
}
