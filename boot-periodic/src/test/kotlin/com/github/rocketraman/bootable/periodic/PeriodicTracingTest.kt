package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.setOfAppServices
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.leader.AlwaysLeaderModule
import com.github.rocketraman.bootable.leader.LeaderElectionModule
import com.github.rocketraman.bootable.telemetry.otel.ALWAYS_SAMPLE
import com.github.rocketraman.bootable.telemetry.otel.MetricsConfig
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizers
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.Sample
import com.github.rocketraman.bootable.telemetry.otel.TelemetryConfig
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.inBindSet
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.instance
import org.kodein.di.singleton
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

/** Collects every span the SDK ends, in memory, as a test's window onto it. */
class InMemorySpans : OpenTelemetryCustomizer {
  val exporter: InMemorySpanExporter = InMemorySpanExporter.create()

  override fun customizeTracerProvider(builder: SdkTracerProviderBuilder) {
    builder.addSpanProcessor(SimpleSpanProcessor.create(exporter))
  }

  val spans: List<SpanData> get() = exporter.finishedSpanItems
}

private val loopConfig = PeriodicConfig(interval = 10.milliseconds)

/** Runs [loop] until it has run twice, then stops it, and returns the spans of its runs. */
private suspend fun traceRuns(loop: AppService, spans: InMemorySpans): List<SpanData> {
  loop.start(CoroutineScope(SupervisorJob()))
  try {
    eventually { spans.spans.count { it.name == "periodic ${loop.name()}" } >= 2 }
  } finally {
    loop.shutdown()
  }
  return spans.spans.filter { it.name == "periodic ${loop.name()}" }
}

private fun telemetryService(spans: InMemorySpans) =
  OpenTelemetryService(TelemetryConfig(metrics = MetricsConfig(runtime = false), registerGlobal = false), listOf(spans))

sealed interface TracingTestScope

@DependencyGraph(
  TracingTestScope::class,
  additionalScopes = [BootScope::class],
  bindingContainers = [TracingTestBindings::class],
)
interface TracingTestGraph : BootableGraph {
  val inMemorySpans: InMemorySpans
}

// named rather than contributed: a @ContributesTo in test code trips the power-assert bug KT-83931 on the hint file
// Metro generates for it. It provides the OpenTelemetry, as boot-telemetry-otel-metro would, but in this graph only:
// on the test classpath, boot-telemetry-otel-metro would contribute it to the wiring tests' graph too
@BindingContainer
object TracingTestBindings {
  @Provides @SingleIn(TracingTestScope::class) fun inMemorySpans(): InMemorySpans = InMemorySpans()

  @Provides @SingleIn(TracingTestScope::class)
  fun telemetry(spans: InMemorySpans): OpenTelemetryService = telemetryService(spans)

  @Provides fun openTelemetry(telemetry: OpenTelemetryService): OpenTelemetry = telemetry.openTelemetry

  @Provides @IntoSet fun telemetryAppService(telemetry: OpenTelemetryService): AppService = telemetry

  @Provides @IntoSet
  fun tracedLoop(periodic: PeriodicServices): AppService = periodic.create("traced", loopConfig) {}
}

/** An application's module, composed as an Inara application composes it, with the SDK and a loop to trace. */
@DIModule(composed = true)
interface InaraTracingModule : ConfigModule, BootableModule, OpenTelemetryModule, LeaderElectionModule, PeriodicModule {
  @OpenTelemetryCustomizers val inMemorySpans: OpenTelemetryCustomizer
  @AppServices val tracedLoop: PeriodicAppService
}

private fun inaraTracingModule(
  spans: InMemorySpans,
  tracingOptions: PeriodicTracingOptions = PeriodicTracingOptions(),
) =
  InaraTracingModule(
    configModule = hopliteConfigModule(),
    bootableModule = bootableModule(),
    openTelemetryModule = openTelemetryModule(),
    leaderElectionModule = AlwaysLeaderModule,
    periodicModule = periodicModule(tracingOptions = tracingOptions),
    inMemorySpans = Binding.value(spans),
    tracedLoop = Binding.single { periodicServices.create("traced", loopConfig) {} },
  )

val PeriodicTracingTest by testSuite {
  test("each run is a root span of its own, named for the service, with the run's spans within it") {
    val spans = InMemorySpans()
    val telemetry = telemetryService(spans)
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
    val telemetry = telemetryService(spans)
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

  test("kodein, through the OpenTelemetry binding") {
    val runs = AtomicInteger()
    val di = DI {
      import(bootableModule)
      import(hopliteConfigModule)
      import(openTelemetryModule)
      import(periodicModule)
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

  test("metro, through the graph's OpenTelemetry") {
    val graph = createGraph<TracingTestGraph>()
    val telemetry = graph.appServices.filterIsInstance<OpenTelemetryService>().single()
    try {
      val loop = graph.appServices.filterIsInstance<PeriodicAppService>().single()
      assert(traceRuns(loop, graph.inMemorySpans).isNotEmpty())
    } finally {
      telemetry.shutdown()
    }
  }

  test("inara, through the OpenTelemetryModule's OpenTelemetry") {
    val spans = InMemorySpans()
    val module = inaraTracingModule(spans)
    try {
      val loop = module.setOfAppServices().filterIsInstance<PeriodicAppService>().single()
      assert(traceRuns(loop, spans).isNotEmpty())
    } finally {
      module.openTelemetryService.shutdown()
    }
  }

  test("kodein, not traced without an OpenTelemetry binding") {
    val di = DI {
      import(bootableModule)
      import(periodicModule)
    }.direct
    assert(di.instance<PeriodicServices>().interceptors.isEmpty())
  }

  test("kodein, not traced when the options turn it off") {
    val di = DI {
      import(bootableModule)
      import(hopliteConfigModule)
      import(openTelemetryModule)
      import(periodicModule)
      bindSingleton { PeriodicTracingOptions(enabled = false) }
    }.direct
    val telemetry = di.instance<OpenTelemetryService>()
    try {
      assert(di.instance<PeriodicServices>().interceptors.isEmpty())
    } finally {
      telemetry.shutdown()
    }
  }

  test("inara, not traced when the options turn it off") {
    val module = inaraTracingModule(InMemorySpans(), PeriodicTracingOptions(enabled = false))
    try {
      assert(module.periodicServices.interceptors.isEmpty())
    } finally {
      module.openTelemetryService.shutdown()
    }
  }

  test("options: tracing through an OpenTelemetry, unless it is the no-op one, or they turn it off") {
    val spans = InMemorySpans()
    val telemetry = telemetryService(spans)
    try {
      assert(PeriodicTracingOptions().interceptor(telemetry.openTelemetry) != null)
      assert(PeriodicTracingOptions().interceptor(OpenTelemetry.noop()) == null)
      assert(PeriodicTracingOptions(enabled = false).interceptor(telemetry.openTelemetry) == null)
    } finally {
      telemetry.shutdown()
    }
  }

  test("options: always sampled, when asked") {
    val spans = InMemorySpans()
    val telemetry = telemetryService(spans)
    val tracing = checkNotNull(PeriodicTracingOptions(sample = Sample.Always).interceptor(telemetry.openTelemetry))
    try {
      val runs = traceRuns(PeriodicAppService("rare", loopConfig, interceptors = listOf(tracing)) {}, spans)
      assert(runs.all { it.attributes.get(ALWAYS_SAMPLE) == true })
    } finally {
      telemetry.shutdown()
    }
  }
}
