package com.github.rocketraman.bootable.test

import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/**
 * A Kodein module that binds an [InMemoryTelemetry], and adds it to the customizers of `openTelemetryModule`, so a test
 * collects the spans and metrics recorded through it. Import it after `openTelemetryModule`, which opens that set:
 *
 * ```
 * val di = DI {
 *   import(bootableModule)
 *   import(hopliteConfigModule)
 *   import(openTelemetryModule)
 *   import(inMemoryTelemetryModule)
 * }
 * val telemetry = di.direct.instance<InMemoryTelemetry>()
 * ```
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val inMemoryTelemetryModule by DI.Module {
  bindSingleton { InMemoryTelemetry() }
  inBindSet<OpenTelemetryCustomizer> { add { singleton { instance<InMemoryTelemetry>() } } }
}
