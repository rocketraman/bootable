package com.github.rocketraman.bootable.scheduler.db

import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.setOfAppServices
import com.github.rocketraman.bootable.boot.startPlan
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.db.flyway.FlywayConfig
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationModule
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.flyway.flywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.HikariConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.HikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import com.github.rocketraman.bootable.db.jdbc.SqliteConfigSource
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.jdbcTracing
import com.github.rocketraman.bootable.db.jdbc.sqliteConfigModule
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
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.time.Clock
import kotlin.time.toJavaInstant

/** Collects every span the SDK ends, in memory, as a test's window onto it. */
class InMemorySpans : OpenTelemetryCustomizer {
  val exporter: InMemorySpanExporter = InMemorySpanExporter.create()

  override fun customizeTracerProvider(builder: SdkTracerProviderBuilder) {
    builder.addSpanProcessor(SimpleSpanProcessor.create(exporter))
  }

  val spans: List<SpanData> get() = exporter.finishedSpanItems
}

/** One-time tasks whose executions start a span of their own within the execution's, or fail. */
class TestTasks : DbSchedulerTaskProvider {
  val greet: OneTimeTask<Void> = Tasks.oneTime("greet").execute { _, _ ->
    // the execution's span is current, so a span the task starts is its child
    check(Span.current().spanContext.isValid)
    Span.current().addEvent("greeted")
  }

  val broken: OneTimeTask<Void> = Tasks.oneTime("broken")
    .onFailure { _, _ -> } // the default retries; the test needs only the one execution
    .execute { _, _ -> error("broken") }

  override fun tasks() = listOf(greet, broken)
}

private fun SchedulerClient.scheduleNow(task: OneTimeTask<Void>, id: String) =
  scheduleIfNotExists(task.instance(id), Clock.System.now().toJavaInstant())

/** Waits for the execution span of [task] to end, and returns it. */
private fun InMemorySpans.executionSpan(task: String): SpanData {
  eventually { spans.any { it.name == "db-scheduler $task" } }
  return spans.single { it.name == "db-scheduler $task" }
}

private fun telemetryService(spans: InMemorySpans) =
  OpenTelemetryService(TelemetryConfig(metrics = MetricsConfig(runtime = false), registerGlobal = false), listOf(spans))

/**
 * Runs a scheduler over a migrated SQLite database whose statements are traced, with [TestTasks] and the tracing of
 * [sample], and runs [block] with it.
 */
private fun withTracedScheduler(
  sample: Sample = Sample.BySampler,
  block: (spans: InMemorySpans, tasks: TestTasks, scheduler: SchedulerClient) -> Unit,
) = withTempDir { dir ->
  val spans = InMemorySpans()
  val telemetry = telemetryService(spans)
  val pool = JdbcDataSourceService(
    SqliteConfig(path = dir.resolve("scheduler.db").toString()), HikariConnectionPoolFactory(),
    jdbcTracing(telemetry.openTelemetry),
  )
  pool.start()
  try {
    FlywayMigrationService(pool.dataSource, FlywayConfig(locations = listOf("classpath:bootable/db-scheduler/sqlite")))
      .start()
    val tasks = TestTasks()
    val service = DbSchedulerService(
      pool.dataSource, testSchedulerConfig, tasks.tasks(),
      customizer = dbSchedulerTracing(telemetry.openTelemetry, sample),
    )
    service.start()
    try {
      block(spans, tasks, service.scheduler)
    } finally {
      service.shutdown()
    }
  } finally {
    pool.shutdown()
    telemetry.shutdown()
  }
}

sealed interface TracingTestScope

@DependencyGraph(
  TracingTestScope::class,
  additionalScopes = [BootScope::class],
  bindingContainers = [TracingTestBindings::class],
)
interface TracingTestGraph : BootableGraph {
  val inMemorySpans: InMemorySpans
  val tasks: TestTasks
  val schedulerClient: SchedulerClient
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

  @Provides @SingleIn(TracingTestScope::class) fun tasks(): TestTasks = TestTasks()

  @Provides @IntoSet fun taskProvider(tasks: TestTasks): DbSchedulerTaskProvider = tasks
}

@DIModule(composed = true)
interface InaraTracingTestModule :
  ConfigModule,
  BootableModule,
  OpenTelemetryModule,
  JdbcDataSourceModule,
  HikariPoolModule,
  FlywayMigrationModule,
  DbSchedulerModule {
  @OpenTelemetryCustomizers val inMemorySpans: InMemorySpans
  @DbSchedulerTaskProviders val tasks: TestTasks
}

private fun inaraTracingTestModule() = InaraTracingTestModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),
  jdbcDataSourceModule = jdbcDataSourceModule(SqliteConfigSource),
  hikariPoolModule = hikariPoolModule(),
  flywayMigrationModule = flywayMigrationModule(),
  dbSchedulerModule = dbSchedulerModule(),
  inMemorySpans = Binding.single { InMemorySpans() },
  tasks = Binding.single { TestTasks() },
)

/** Starts the services in their planned order, runs an execution, checks it is traced, and shuts them down. */
private fun Set<AppService>.startAndTrace(spans: InMemorySpans, tasks: TestTasks, client: SchedulerClient) {
  // a fresh database each time, so the migration runs
  listOf("", "-wal", "-shm").forEach { Path("build/scheduler-wiring-test.db$it").deleteIfExists() }
  val ordered = startPlan(this)
  ordered.forEach { it.start() }
  try {
    client.scheduleNow(tasks.greet, "wired")
    val execution = spans.executionSpan("greet")
    assert(!execution.parentSpanContext.isValid)
    assert(execution.attributes.get(DB_SCHEDULER_INSTANCE) == "wired")
  } finally {
    ordered.asReversed().forEach { it.shutdown() }
  }
}

val DbSchedulerTracingTest by testSuite {
  test("each execution is a root span of its own, named for the task, with its statements within it") {
    withTracedScheduler { spans, tasks, scheduler ->
      scheduler.scheduleNow(tasks.greet, "g1")
      val execution = spans.executionSpan("greet")
      assert(!execution.parentSpanContext.isValid)
      assert(execution.kind == SpanKind.INTERNAL)
      assert(execution.status.statusCode == StatusCode.UNSET)
      assert(execution.attributes.get(DB_SCHEDULER_TASK) == "greet")
      assert(execution.attributes.get(DB_SCHEDULER_INSTANCE) == "g1")
      assert(execution.attributes.get(DB_SCHEDULER_CONSECUTIVE_FAILURES) == 0L)
      assert(execution.attributes.get(ALWAYS_SAMPLE) == null)
      assert(execution.events.any { it.name == "greeted" })
      // the completion, which deletes the one-time execution's row, is within the execution's span
      val within = spans.spans.filter { it.parentSpanId == execution.spanId }
      assert(within.any { it.kind == SpanKind.CLIENT && it.name.startsWith("DELETE") })
      // the scheduler's own statements, such as its polls, are not
      assert(spans.spans.any { it.kind == SpanKind.CLIENT && !it.parentSpanContext.isValid })
    }
  }

  test("an execution that fails ends its span with an error, and the exception") {
    withTracedScheduler { spans, tasks, scheduler ->
      scheduler.scheduleNow(tasks.broken, "b1")
      val execution = spans.executionSpan("broken")
      assert(execution.status.statusCode == StatusCode.ERROR)
      assert(execution.events.any { it.name == "exception" })
    }
  }

  test("always sampled, when asked") {
    withTracedScheduler(Sample.Always) { spans, tasks, scheduler ->
      scheduler.scheduleNow(tasks.greet, "g1")
      assert(spans.executionSpan("greet").attributes.get(ALWAYS_SAMPLE) == true)
    }
  }

  test("kodein, through the OpenTelemetry binding") {
    val di = DI {
      import(bootableModule)
      import(hopliteConfigModule)
      import(openTelemetryModule)
      import(jdbcDataSourceModule)
      import(sqliteConfigModule)
      import(hikariPoolModule)
      import(flywayMigrationModule)
      import(dbSchedulerModule)
      bindSingleton { InMemorySpans() }
      inBindSet<OpenTelemetryCustomizer> { add { singleton { instance<InMemorySpans>() } } }
      bindSingleton { TestTasks() }
      inBindSet<DbSchedulerTaskProvider> { add { singleton { instance<TestTasks>() } } }
    }.direct
    di.instance<Set<AppService>>().startAndTrace(di.instance(), di.instance(), di.instance())
  }

  test("metro, through the graph's OpenTelemetry") {
    val graph = createGraph<TracingTestGraph>()
    graph.appServices.startAndTrace(graph.inMemorySpans, graph.tasks, graph.schedulerClient)
  }

  test("inara, through the OpenTelemetryModule's OpenTelemetry") {
    val module = inaraTracingTestModule()
    module.setOfAppServices().startAndTrace(module.inMemorySpans, module.tasks, module.schedulerClient)
  }

  test("options: tracing through an OpenTelemetry, unless it is the no-op one, or they turn it off") {
    val telemetry = telemetryService(InMemorySpans())
    try {
      assert(DbSchedulerTracingOptions().customizer(telemetry.openTelemetry) != null)
      assert(DbSchedulerTracingOptions().customizer(OpenTelemetry.noop()) == null)
      assert(DbSchedulerTracingOptions(enabled = false).customizer(telemetry.openTelemetry) == null)
    } finally {
      telemetry.shutdown()
    }
  }
}
