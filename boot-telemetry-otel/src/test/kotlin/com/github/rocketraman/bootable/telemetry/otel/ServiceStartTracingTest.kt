package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.LifecycleController
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.boot.ServiceContext
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.samplers.Sampler
import org.kodein.di.DI
import org.kodein.di.bindInstance
import org.kodein.di.inBindSet
import org.kodein.di.singleton
import org.kodein.di.direct
import org.kodein.di.instance
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Clock
import kotlin.time.Instant as KotlinInstant
import kotlin.time.TimeSource

@DependencyGraph(
  TestScope::class,
  additionalScopes = [BootScope::class],
  bindingContainers = [TestTelemetryBindings::class, ServiceStartTracingBindings::class],
)
interface StartTracingGraph : BootableGraph

sealed interface ClockedTestScope

@DependencyGraph(
  ClockedTestScope::class,
  additionalScopes = [BootScope::class],
  bindingContainers = [ClockedTestBindings::class, ServiceStartTracingBindings::class],
)
interface ClockedStartTracingGraph : BootableGraph {
  val clock: Clock
  val inMemoryTelemetry: InMemoryTelemetry
  val openTelemetryService: OpenTelemetryService
}

@BindingContainer
object ClockedTestBindings {
  @Provides @SingleIn(ClockedTestScope::class) fun clock(): Clock = FixedClock()

  @Provides @SingleIn(ClockedTestScope::class) fun inMemoryTelemetry(): InMemoryTelemetry = InMemoryTelemetry()

  @Provides @IntoSet fun inMemory(telemetry: InMemoryTelemetry): OpenTelemetryCustomizer = telemetry

  @Provides @IntoSet fun sampleAll(): OpenTelemetryCustomizer = SampleAll
}

/** Does traced work as it starts, as a migration's queries would be, and fails to start if [fail]. */
private class WorkingService(
  private val name: String,
  private val phase: DefaultPhase,
  private val openTelemetry: OpenTelemetry,
  private val fail: Boolean = false,
) : AppService {
  override fun name() = name
  override fun ordering() = Ordering(phase)

  override fun start() {
    openTelemetry.getTracer("test").spanBuilder("$name.work").startSpan().end()
    check(!fail) { "$name failed to start" }
  }
}

/** A clock stopped at [at], far from now, so a span timed by it can't be mistaken for one timed by the system clock. */
private class FixedClock(private val at: KotlinInstant = KotlinInstant.parse("2030-01-01T00:00:00Z")) : Clock {
  override fun now() = at
}

/** Samples every trace, overriding the test config's `sampleRatio`, so a test's spans are always recorded. */
private object SampleAll : OpenTelemetryCustomizer {
  override fun customizeSampler(sampler: Sampler): Sampler = Sampler.alwaysOn()
}

private val Long.startInstant: KotlinInstant get() = KotlinInstant.fromEpochSeconds(0, this)

/** Runs a start with one service, traced by [tracing], and returns the root span's start and the service span's. */
private fun traceStart(tracing: ServiceStartTracing, telemetry: InMemoryTelemetry): Pair<KotlinInstant, KotlinInstant> {
  tracing.aroundAppStart(ServiceContext(emptySet(), emptyList(), {}, {}), TimeSource.Monotonic.markNow() - 1.seconds) {
    val pool = object : AppService {
      override fun name() = "pool"
      override fun start() = Unit
    }
    tracing.aroundServiceStart(pool, pool::start)
  }
  val spans = telemetry.spans.finishedSpanItems
  return spans.single { it.name == ServiceStartTracing.APP_START_SPAN }.startEpochNanos.startInstant to
    spans.single { it.name == "start pool" }.startEpochNanos.startInstant
}

/** Checks the start was timed by [clock]: the root a second or so before its time, and the service's span at it. */
private fun assertTimedBy(clock: FixedClock, times: Pair<KotlinInstant, KotlinInstant>) {
  val (root, service) = times
  val now = clock.now()
  assert(root <= now - 1.seconds && root > now - 10.seconds)
  assert(service == now)
}

val ServiceStartTracingTest by testSuite {
  test("the SDK and the root span take their time from the clock they are given") {
    val clock = FixedClock()
    val telemetry = InMemoryTelemetry()
    val service = OpenTelemetryService(testConfig(), listOf(telemetry), clock)
    try {
      assertTimedBy(clock, traceStart(ServiceStartTracing(service.openTelemetry, clock), telemetry))
    } finally {
      service.shutdown()
    }
  }

  test("the start is one trace, from the startup mark, with a span for each service's start and the work within it") {
    val telemetry = InMemoryTelemetry()
    val service = OpenTelemetryService(testConfig(), listOf(telemetry))
    val openTelemetry = service.openTelemetry
    val tracing = ServiceStartTracing(openTelemetry)
    val context = ServiceContext(emptySet(), emptyList(), {}, {})
    try {
      val beforeBoot = Instant.now().minusSeconds(2)
      tracing.aroundAppStart(context, TimeSource.Monotonic.markNow() - 1.seconds) {
        val pool = WorkingService("pool", DefaultPhase.Infrastructure, openTelemetry)
        tracing.aroundServiceStart(pool, pool::start)
        val app = WorkingService("app", DefaultPhase.Application, openTelemetry)
        tracing.aroundServiceStart(app, app::start)
      }
      val spans = telemetry.spans.finishedSpanItems
      val root = spans.single { it.name == ServiceStartTracing.APP_START_SPAN }
      assert(!root.parentSpanContext.isValid)
      assert(root.instrumentationScopeInfo.name == ServiceStartTracing.INSTRUMENTATION_SCOPE)
      assert(root.status.statusCode == StatusCode.UNSET)
      // backdated to the startup mark, a second before the start was called
      val rootStart = Instant.ofEpochSecond(0, root.startEpochNanos)
      assert(rootStart.isAfter(beforeBoot) && Duration.between(rootStart, Instant.now()) >= Duration.ofSeconds(1))
      for (name in listOf("pool", "app")) {
        val start = spans.single { it.name == "start $name" }
        assert(start.parentSpanId == root.spanId)
        val work = spans.single { it.name == "$name.work" }
        assert(work.parentSpanId == start.spanId)
        assert(work.traceId == root.traceId)
      }
    } finally {
      service.shutdown()
    }
  }

  test("a service whose start fails has an error status, as does the application's start") {
    val telemetry = InMemoryTelemetry()
    val service = OpenTelemetryService(testConfig(), listOf(telemetry))
    val openTelemetry = service.openTelemetry
    val tracing = ServiceStartTracing(openTelemetry)
    try {
      val failure = runCatching {
        tracing.aroundAppStart(ServiceContext(emptySet(), emptyList(), {}, {}), TimeSource.Monotonic.markNow()) {
          val migrate = WorkingService("migrate", DefaultPhase.Initialization, openTelemetry, fail = true)
          tracing.aroundServiceStart(migrate, migrate::start)
        }
      }.exceptionOrNull()
      assert(failure is IllegalStateException)
      val spans = telemetry.spans.finishedSpanItems
      for (name in listOf("start migrate", ServiceStartTracing.APP_START_SPAN)) {
        val span = spans.single { it.name == name }
        assert(span.status.statusCode == StatusCode.ERROR)
        assert(span.events.single().name == "exception")
      }
    } finally {
      service.shutdown()
    }
  }

  test("a service started outside the application's start, with no span current, has a trace of its own") {
    val telemetry = InMemoryTelemetry()
    val service = OpenTelemetryService(testConfig(), listOf(telemetry))
    val openTelemetry = service.openTelemetry
    try {
      val pool = WorkingService("pool", DefaultPhase.Infrastructure, openTelemetry)
      ServiceStartTracing(openTelemetry).aroundServiceStart(pool, pool::start)
      assert(!telemetry.spans.finishedSpanItems.single { it.name == "start pool" }.parentSpanContext.isValid)
    } finally {
      service.shutdown()
    }
  }

  testSuite("wired by") {
    test("kodein") {
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(openTelemetryModule)
        import(serviceStartTracingModule)
      }.direct
      assert(di.instance<Set<LifecycleController>>().filterIsInstance<ServiceStartTracing>().size == 1)
    }

    test("kodein, with a clock") {
      val clock = FixedClock()
      val telemetry = InMemoryTelemetry()
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(openTelemetryModule)
        import(serviceStartTracingModule)
        bindInstance<Clock> { clock }
        inBindSet<OpenTelemetryCustomizer> { add { singleton { telemetry } } }
        inBindSet<OpenTelemetryCustomizer> { add { singleton { SampleAll } } }
      }.direct
      val tracing = di.instance<Set<LifecycleController>>().filterIsInstance<ServiceStartTracing>().single()
      try {
        assertTimedBy(clock, traceStart(tracing, telemetry))
      } finally {
        di.instance<OpenTelemetryService>().shutdown()
      }
    }

    test("metro, with a clock") {
      val graph = createGraph<ClockedStartTracingGraph>()
      val tracing = graph.lifecycleControllers.filterIsInstance<ServiceStartTracing>().single()
      try {
        assertTimedBy(graph.clock as FixedClock, traceStart(tracing, graph.inMemoryTelemetry))
      } finally {
        graph.openTelemetryService.shutdown()
      }
    }

    test("metro, only when named in the graph") {
      assert(createGraph<StartTracingGraph>().lifecycleControllers.filterIsInstance<ServiceStartTracing>().size == 1)
      assert(createGraph<TestGraph>().lifecycleControllers.none { it is ServiceStartTracing })
    }
  }
}
