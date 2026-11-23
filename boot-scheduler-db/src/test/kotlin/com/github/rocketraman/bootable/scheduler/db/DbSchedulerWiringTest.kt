@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.scheduler.db

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.koin.bootableModule as koinBootableModule
import com.github.rocketraman.bootable.boot.setOfAppServices
import com.github.rocketraman.bootable.boot.startPlan
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule as koinHopliteConfigModule
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationModule
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.flyway.flywayMigrationModule
import com.github.rocketraman.bootable.db.flyway.koin.flywayMigrationModule as koinFlywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.HikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfigSource
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.koin.hikariPoolModule as koinHikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.koin.jdbcDataSourceModule as koinJdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.koin.sqliteConfigModule as koinSqliteConfigModule
import com.github.rocketraman.bootable.db.jdbc.sqliteConfigModule
import com.github.rocketraman.bootable.scheduler.db.koin.dbSchedulerHealthCheckModule as koinDbSchedulerHealthCheckModule
import com.github.rocketraman.bootable.scheduler.db.koin.dbSchedulerModule as koinDbSchedulerModule
import com.github.rocketraman.bootable.telemetry.otel.NoopOpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryApiModule
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

sealed interface TestScope

@DependencyGraph(
  TestScope::class,
  additionalScopes = [BootScope::class],
  // named rather than contributed: a @ContributesTo in test code trips the power-assert bug KT-83931 on the hint
  // file Metro generates for it
  bindingContainers = [TestBindings::class, DbSchedulerHealthCheckBindings::class],
)
interface TestGraph : BootableGraph {
  val record: TaskRecord
  val schedulerClient: SchedulerClient
  val health: HealthLifecycleController
}

@BindingContainer
object TestBindings {
  @Provides @SingleIn(TestScope::class) fun record(): TaskRecord = TaskRecord()

  @Provides @IntoSet fun tasks(record: TaskRecord): DbSchedulerTaskProvider = record
}

@DIModule(composed = true)
interface InaraWiringTestModule :
  ConfigModule,
  BootableModule,
  OpenTelemetryApiModule,
  JdbcDataSourceModule,
  HikariPoolModule,
  FlywayMigrationModule,
  DbSchedulerModule,
  DbSchedulerHealthCheckModule {
  @DbSchedulerTaskProviders val record: TaskRecord
}

private fun inaraWiringTestModule() = InaraWiringTestModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryApiModule = NoopOpenTelemetryModule,
  jdbcDataSourceModule = jdbcDataSourceModule(SqliteConfigSource),
  hikariPoolModule = hikariPoolModule(),
  flywayMigrationModule = flywayMigrationModule(),
  dbSchedulerModule = dbSchedulerModule(),
  dbSchedulerHealthCheckModule = dbSchedulerHealthCheckModule(),
  record = Binding.single { TaskRecord() },
)

/**
 * Starts the services in their planned order, as Bootable does, checks the migrated scheduler runs the tasks, and
 * shuts them down.
 */
private suspend fun Set<AppService>.startAndRun(record: TaskRecord, client: SchedulerClient) {
  // a fresh database each time, so the migration runs
  listOf("", "-wal", "-shm").forEach { Path("build/scheduler-wiring-test.db$it").deleteIfExists() }
  // shuffled, so the order holds because the services declare it, not by accident
  val ordered = startPlan(this, tiebreaker = StartOrder.Tiebreaker.Shuffled())
  assert(
    ordered.map { it::class } ==
      listOf(JdbcDataSourceService::class, FlywayMigrationService::class, DbSchedulerService::class)
  )
  ordered.forEach { it.start() }
  try {
    client.schedule(record.reminder.instance("r1", Reminder("ada", "wired")), Clock.System.now())
    eventually { record.recurringRuns.get() >= 1 }
    eventually { record.reminders == listOf(Reminder("ada", "wired")) }
  } finally {
    ordered.asReversed().forEach { it.shutdown() }
  }
}

/** Config comes from src/test/resources/application.conf, as it would in an application. */
val DbSchedulerWiringTest by testSuite {
  testSuite("scheduler config decoded by") {
    test("hoplite") {
      val config = hopliteConfigBinder(report = false).config<DbSchedulerConfig>("db.scheduler")
      assert(
        config == DbSchedulerConfig(
          threads = 3,
          pollingInterval = 100.milliseconds,
          immediateExecution = true,
          shutdownMaxWait = 5.seconds,
          schedulerName = "wiring-test",
        )
      )
    }

    test("hoplite, as all defaults when the section is absent") {
      assert(hopliteConfigBinder(report = false).config<DbSchedulerConfig>("absent.scheduler") == DbSchedulerConfig())
    }

    test("kodein") {
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(jdbcDataSourceModule)
        import(sqliteConfigModule)
        import(hikariPoolModule)
        import(flywayMigrationModule)
        import(dbSchedulerModule)
        import(dbSchedulerHealthCheckModule)
        bindSingleton { TaskRecord() }
        inBindSet<DbSchedulerTaskProvider> { add { singleton { instance<TaskRecord>() } } }
      }.direct

      assert(di.instance<Scheduler>() === di.instance<DbSchedulerService>().scheduler)
      assert(di.instance<HealthLifecycleController>().checks.single() is DbSchedulerHealthCheck)
      di.instance<Set<AppService>>().startAndRun(di.instance(), di.instance())
    }

    test("koin") {
      val koin = koinApplication {
        modules(
          koinBootableModule(),
          koinHopliteConfigModule(),
          koinJdbcDataSourceModule(),
          koinSqliteConfigModule(),
          koinHikariPoolModule(),
          koinFlywayMigrationModule(),
          koinDbSchedulerModule(),
          koinDbSchedulerHealthCheckModule(),
          module {
            single { TaskRecord() }
            single<DbSchedulerTaskProvider>(named<TaskRecord>()) { get<TaskRecord>() }
          },
        )
      }.koin

      try {
        assert(koin.get<Scheduler>() === koin.get<DbSchedulerService>().scheduler)
        assert(koin.get<HealthLifecycleController>().checks.single() is DbSchedulerHealthCheck)
        koin.getAll<AppService>().toSet().startAndRun(koin.get(), koin.get())
      } finally {
        koin.close()
      }
    }

    test("metro") {
      val graph = createGraph<TestGraph>()
      assert(graph.health.checks.single() is DbSchedulerHealthCheck)
      graph.appServices.startAndRun(graph.record, graph.schedulerClient)
    }

    test("inara") {
      val module = inaraWiringTestModule()

      assert(module.scheduler === module.dbSchedulerService.scheduler)
      assert(module.health.checks.single() is DbSchedulerHealthCheck)
      module.setOfAppServices().startAndRun(module.record, module.schedulerClient)
    }
  }
}
