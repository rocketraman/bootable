package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.boot.AdvancedAppService
import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.telemetry.otel.MetricsConfig
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.TelemetryConfig
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Collects every span the SDK ends, in memory, as a test's window onto it. */
class InMemorySpans : OpenTelemetryCustomizer {
  val exporter: InMemorySpanExporter = InMemorySpanExporter.create()

  override fun customizeTracerProvider(builder: SdkTracerProviderBuilder) {
    builder.addSpanProcessor(SimpleSpanProcessor.create(exporter))
  }

  val spans: List<SpanData> get() = exporter.finishedSpanItems
}

private fun eventually(timeout: Duration = 5.seconds, condition: () -> Boolean) {
  val start = TimeSource.Monotonic.markNow()
  while (!condition()) {
    check(start.elapsedNow() < timeout) { "Condition not met within $timeout" }
    Thread.sleep(5)
  }
}

private val loopConfig = PeriodicConfig(interval = 10.milliseconds)

/** Runs [loop] until it has run twice, then stops it, and returns the spans of its runs. */
private fun traceRuns(loop: AppService, spans: InMemorySpans): List<SpanData> {
  (loop as AdvancedAppService).start { error("died") }
  try {
    eventually { spans.spans.count { it.name == "periodic ${loop.name()}" } >= 2 }
  } finally {
    loop.shutdown()
  }
  return spans.spans.filter { it.name == "periodic ${loop.name()}" }
}

sealed interface TestScope

@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class], bindingContainers = [TestBindings::class])
interface TestGraph : BootableGraph {
  val inMemorySpans: InMemorySpans
}

// named rather than contributed: a @ContributesTo in test code trips the power-assert bug KT-83931 on the hint file
// Metro generates for it
@BindingContainer
object TestBindings {
  @Provides @SingleIn(TestScope::class) fun inMemorySpans(): InMemorySpans = InMemorySpans()

  @Provides @IntoSet fun inMemory(spans: InMemorySpans): OpenTelemetryCustomizer = spans

  @Provides @IntoSet
  fun tracedLoop(periodic: PeriodicServices): AppService = periodic.create("traced", loopConfig) {}
}

val PeriodicTracingTest by testSuite {
  test("each run is a root span of its own, named for the service, with the run's spans within it") {
    val spans = InMemorySpans()
    val telemetry = OpenTelemetryService(
      TelemetryConfig(metrics = MetricsConfig(runtime = false), registerGlobal = false), listOf(spans),
    )
    val tracer = telemetry.openTelemetry.getTracer("test")
    val loop = PeriodicAppService("sweep", loopConfig, interceptors = listOf(periodicTracing(telemetry.openTelemetry))) {
      // the run's span is current, so a span the work starts is its child
      check(Span.current().spanContext.isValid)
      tracer.spanBuilder("work").startSpan().end()
    }
    try {
      val runs = traceRuns(loop, spans)
      assert(runs.all { !it.parentSpanContext.isValid })
      assert(runs.map { it.traceId }.toSet().size == runs.size)
      assert(runs.all { it.attributes.get(PERIODIC_SERVICE) == "sweep" })
      val work = spans.spans.filter { it.name == "work" }
      assert(work.isNotEmpty())
      assert(work.all { child -> runs.any { it.spanId == child.parentSpanId } })
    } finally {
      telemetry.shutdown()
    }
  }

  test("a run that fails ends its span with an error, and the exception") {
    val spans = InMemorySpans()
    val telemetry = OpenTelemetryService(
      TelemetryConfig(metrics = MetricsConfig(runtime = false), registerGlobal = false), listOf(spans),
    )
    val loop = PeriodicAppService(
      "failing", loopConfig, interceptors = listOf(periodicTracing(telemetry.openTelemetry)),
      onFailure = { _, _, _ -> },
    ) { error("broken") }
    try {
      val runs = traceRuns(loop, spans)
      assert(runs.all { it.status.statusCode == StatusCode.ERROR })
      assert(runs.all { span -> span.events.any { it.name == "exception" } })
    } finally {
      telemetry.shutdown()
    }
  }

  test("kodein") {
    val runs = AtomicInteger()
    val di = DI {
      import(bootableModule)
      import(hopliteConfigModule)
      import(openTelemetryModule)
      import(periodicModule)
      import(periodicTracingModule())
      bindSingleton { InMemorySpans() }
      inBindSet<OpenTelemetryCustomizer> { add { singleton { instance<InMemorySpans>() } } }
      inBindSet<AppService> {
        add { singleton { instance<PeriodicServices>().create("traced", loopConfig) { runs.incrementAndGet() } } }
      }
    }.direct
    val telemetry = di.instance<OpenTelemetryService>()
    try {
      val loop = di.instance<Set<AppService>>().filterIsInstance<PeriodicAppService>().single()
      assert(traceRuns(loop, di.instance()).isNotEmpty())
    } finally {
      telemetry.shutdown()
    }
  }

  test("metro") {
    val graph = createGraph<TestGraph>()
    val telemetry = graph.appServices.filterIsInstance<OpenTelemetryService>().single()
    try {
      val loop = graph.appServices.filterIsInstance<PeriodicAppService>().single()
      assert(traceRuns(loop, graph.inMemorySpans).isNotEmpty())
    } finally {
      telemetry.shutdown()
    }
  }
}
