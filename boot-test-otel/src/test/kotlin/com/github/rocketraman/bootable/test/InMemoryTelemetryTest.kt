@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.test

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.koin.bootableModule as koinBootableModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule as koinHopliteConfigModule
import com.github.rocketraman.bootable.telemetry.otel.MetricsConfig
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.TelemetryConfig
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule as koinOpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.test.koin.inMemoryTelemetryModule as koinInMemoryTelemetryModule
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import dev.zacsweers.metro.createGraphFactory
import io.opentelemetry.api.OpenTelemetry
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.inara.DIModule
import org.kodein.di.instance
import kotlin.time.Clock
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** What the code under test would do: record a span, and count. */
private fun OpenTelemetry.record() {
  getTracer("test").spanBuilder("work").startSpan().end()
  getMeter("test").counterBuilder("work.done").build().add(1)
}

internal sealed interface TestScope

/** A graph of Bootable's OpenTelemetry bindings, with the in-memory telemetry included. */
@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class])
internal interface TelemetryGraph : BootableGraph {
  val openTelemetry: OpenTelemetry

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Includes telemetry: InMemoryTelemetryBindings): TelemetryGraph
  }
}

/** A test's module, composed as an Inara application composes it, with the in-memory telemetry. */
@DIModule(composed = true)
internal interface InaraTelemetryModule : ConfigModule, BootableModule, OpenTelemetryModule, InMemoryTelemetryModule

val InMemoryTelemetryTest by testSuite {
  test("collects the spans and metrics of the SDK it customizes, timed by the SDK's clock") {
    val telemetry = InMemoryTelemetry()
    val clock = TestClock()
    val service = OpenTelemetryService(
      TelemetryConfig(serviceName = "test", metrics = MetricsConfig(runtime = false), registerGlobal = false),
      listOf(telemetry),
      clock,
    )

    service.openTelemetry.record()
    val span = telemetry.spans("work").single()
    assert(span.startEpochNanos == clock.now().toEpochMilliseconds() * 1_000_000)
    assert(telemetry.metric("work.done")!!.longSumData.points.single().value == 1L)

    telemetry.resetSpans()
    assert(telemetry.spans.isEmpty())
    service.shutdown()
  }

  test("is added to the SDK's customizers with Kodein") {
    val di = DI {
      import(bootableModule)
      import(hopliteConfigModule)
      import(openTelemetryModule)
      import(inMemoryTelemetryModule)
      bindSingleton<Clock> { TestClock() }
    }.direct

    di.instance<OpenTelemetry>().record()
    assert(di.instance<InMemoryTelemetry>().spans("work").size == 1)
  }

  test("is added to the SDK's customizers with Koin") {
    val koin = koinApplication {
      modules(
        koinBootableModule(),
        koinHopliteConfigModule(),
        koinOpenTelemetryModule(),
        koinInMemoryTelemetryModule(),
        module { single<Clock> { TestClock() } },
      )
    }.koin

    try {
      koin.get<OpenTelemetry>().record()
      assert(koin.get<InMemoryTelemetry>().spans("work").size == 1)
    } finally {
      koin.close()
    }
  }

  test("is added to the SDK's customizers with Metro") {
    val telemetry = InMemoryTelemetry()
    val graph = createGraphFactory<TelemetryGraph.Factory>().create(InMemoryTelemetryBindings(telemetry))

    graph.openTelemetry.record()
    assert(telemetry.spans("work").size == 1)
    assert(telemetry.metric("work.done") != null)
  }

  test("is added to the SDK's customizers with Inara") {
    val module = InaraTelemetryModule(
      configModule = hopliteConfigModule(),
      bootableModule = bootableModule(),
      openTelemetryModule = openTelemetryModule(TestClock()),
      inMemoryTelemetryModule = inMemoryTelemetryModule(),
    )

    module.openTelemetry.record()
    assert(module.inMemoryTelemetry.spans("work").size == 1)
    assert(module.inMemoryTelemetry.metric("work.done") != null)
  }
}
