@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.telemetry.otel

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.LifecycleController
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.boot.ServiceContext
import com.github.rocketraman.bootable.boot.StartupStage
import com.github.rocketraman.bootable.boot.StartupTimeline
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.koin.bootableModule as koinBootableModule
import com.github.rocketraman.bootable.boot.setOfLifecycleControllers
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule as koinHopliteConfigModule
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule as koinOpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.koin.serviceStartTracingModule as koinServiceStartTracingModule
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.StatusCode
import org.kodein.di.DI
import org.kodein.di.bindInstance
import org.kodein.di.inBindSet
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.singleton
import org.kodein.di.direct
import org.kodein.di.instance
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Clock
import kotlin.time.Instant as KotlinInstant
import kotlin.time.TimeSource
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

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
}

/** An application's module, composed as an Inara application composes it, tracing its start. */
@DIModule(composed = true)
interface InaraStartTracingModule : ConfigModule, BootableModule, OpenTelemetryModule, ServiceStartTracingModule {
  @OpenTelemetryCustomizers val inMemoryTelemetry: InMemoryTelemetry
}

private fun inaraStartTracingModule(clock: Clock = Clock.System) = InaraStartTracingModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(clock),
  serviceStartTracingModule = serviceStartTracingModule(clock),
  inMemoryTelemetry = Binding.single { InMemoryTelemetry() },
)

/** Does traced work as it starts, as a migration's queries would be, and fails to start if [fail]. */
private class WorkingService(
  private val name: String,
  private val phase: DefaultPhase,
  private val openTelemetry: OpenTelemetry,
  private val fail: Boolean = false,
) : AppService {
  override fun name() = name
  override fun ordering() = Ordering(phase)

  override suspend fun start() {
    openTelemetry.getTracer("test").spanBuilder("$name.work").startSpan().end()
    check(!fail) { "$name failed to start" }
  }
}

/** A clock stopped at [at], far from now, so a span timed by it can't be mistaken for one timed by the system clock. */
private class FixedClock(private val at: KotlinInstant = KotlinInstant.parse("2030-01-01T00:00:00Z")) : Clock {
  override fun now() = at
}

/** A timeline that began a second ago, with no stages, as of a [Bootable] run without [boot] that took no time. */
private fun aSecondAgo() = StartupTimeline(TimeSource.Monotonic.markNow() - 1.seconds)

private val Long.startInstant: KotlinInstant get() = KotlinInstant.fromEpochSeconds(0, this)

/** Runs a start with one service, traced by [tracing], and returns the root span's start and the service span's. */
private suspend fun traceStart(tracing: ServiceStartTracing, telemetry: InMemoryTelemetry): Pair<KotlinInstant, KotlinInstant> {
  tracing.aroundAppStart(ServiceContext(emptySet(), emptyList(), {}, {}), aSecondAgo()) {
    val pool = object : AppService {
      override fun name() = "pool"
      override suspend fun start() = Unit
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
      tracing.aroundAppStart(context, aSecondAgo()) {
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

  test("each stage of the boot before the services start is a span, at the times the timeline marked") {
    val clock = FixedClock()
    val telemetry = InMemoryTelemetry()
    val service = OpenTelemetryService(testConfig(), listOf(telemetry), clock)
    val tracing = ServiceStartTracing(service.openTelemetry, clock)
    try {
      val began = TimeSource.Monotonic.markNow() - 3.seconds
      val stages = listOf(
        StartupStage(StartupTimeline.INITIALIZE_JVM_CLASSES, began, began + 1.seconds),
        StartupStage(StartupTimeline.PLAN_START, began + 1.seconds, began + 2.seconds),
      )
      tracing.aroundAppStart(ServiceContext(emptySet(), emptyList(), {}, {}), StartupTimeline(began, stages)) {}
      val spans = telemetry.spans.finishedSpanItems
      val root = spans.single { it.name == ServiceStartTracing.APP_START_SPAN }
      val classes = spans.single { it.name == StartupTimeline.INITIALIZE_JVM_CLASSES }
      val plan = spans.single { it.name == StartupTimeline.PLAN_START }
      assert(classes.parentSpanId == root.spanId && plan.parentSpanId == root.spanId)
      // the first stage begins with the root, and each stage where the one before it ended
      assert(classes.startEpochNanos == root.startEpochNanos)
      assert(plan.startEpochNanos == classes.endEpochNanos)
      assert(plan.endEpochNanos - plan.startEpochNanos == 1.seconds.inWholeNanoseconds)
    } finally {
      service.shutdown()
    }
  }

  test("the start's trace is always sampled, whatever the configured ratio") {
    val telemetry = InMemoryTelemetry()
    val service = OpenTelemetryService(testConfig(traces = TracesConfig(sampleRatio = 0.0)), listOf(telemetry))
    val openTelemetry = service.openTelemetry
    val tracing = ServiceStartTracing(openTelemetry)
    try {
      tracing.aroundAppStart(ServiceContext(emptySet(), emptyList(), {}, {}), aSecondAgo()) {
        val pool = WorkingService("pool", DefaultPhase.Infrastructure, openTelemetry)
        tracing.aroundServiceStart(pool, pool::start)
      }
      openTelemetry.getTracer("test").spanBuilder("request").startSpan().end()
      val names = telemetry.spans.finishedSpanItems.map { it.name }.toSet()
      assert(names == setOf(ServiceStartTracing.APP_START_SPAN, "start pool", "pool.work"))
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
        tracing.aroundAppStart(ServiceContext(emptySet(), emptyList(), {}, {}), StartupTimeline(TimeSource.Monotonic.markNow())) {
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
      }.direct
      val tracing = di.instance<Set<LifecycleController>>().filterIsInstance<ServiceStartTracing>().single()
      try {
        assertTimedBy(clock, traceStart(tracing, telemetry))
      } finally {
        di.instance<OpenTelemetryService>().shutdown()
      }
    }

    test("koin") {
      val koin = koinApplication {
        modules(koinBootableModule(), koinHopliteConfigModule(), koinOpenTelemetryModule(), koinServiceStartTracingModule())
      }.koin
      try {
        assert(koin.getAll<LifecycleController>().filterIsInstance<ServiceStartTracing>().size == 1)
      } finally {
        koin.close()
      }
    }

    test("koin, with a clock") {
      val clock = FixedClock()
      val telemetry = InMemoryTelemetry()
      val koin = koinApplication {
        modules(
          koinBootableModule(),
          koinHopliteConfigModule(),
          koinOpenTelemetryModule(),
          koinServiceStartTracingModule(),
          module {
            single<Clock> { clock }
            single<OpenTelemetryCustomizer>(named("test")) { telemetry }
          },
        )
      }.koin
      val tracing = koin.getAll<LifecycleController>().filterIsInstance<ServiceStartTracing>().single()
      try {
        assertTimedBy(clock, traceStart(tracing, telemetry))
      } finally {
        koin.get<OpenTelemetryService>().shutdown()
        koin.close()
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

    test("inara") {
      assert(inaraStartTracingModule().setOfLifecycleControllers().filterIsInstance<ServiceStartTracing>().size == 1)
    }

    test("inara, with a clock") {
      val clock = FixedClock()
      val module = inaraStartTracingModule(clock)
      val tracing = module.setOfLifecycleControllers().filterIsInstance<ServiceStartTracing>().single()
      try {
        assertTimedBy(clock, traceStart(tracing, module.inMemoryTelemetry))
      } finally {
        module.openTelemetryService.shutdown()
      }
    }

    test("inara, only when composed") {
      assert(inaraTelemetryModule().setOfLifecycleControllers().none { it is ServiceStartTracing })
    }
  }
}
