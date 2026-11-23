package com.github.rocketraman.bootable.test.koin

import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.test.InMemoryTelemetry
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A Koin module that defines an [InMemoryTelemetry], and adds it to the customizers of `openTelemetryModule()`, so a
 * test collects the spans and metrics recorded through it:
 *
 * ```
 * val koin = koinApplication {
 *   modules(bootableModule(), hopliteConfigModule(), openTelemetryModule(), inMemoryTelemetryModule())
 * }.koin
 * val telemetry = koin.get<InMemoryTelemetry>()
 * ```
 */
fun inMemoryTelemetryModule(): Module = module {
  single { InMemoryTelemetry() }
  single<OpenTelemetryCustomizer>(named<InMemoryTelemetry>()) { get<InMemoryTelemetry>() }
}
