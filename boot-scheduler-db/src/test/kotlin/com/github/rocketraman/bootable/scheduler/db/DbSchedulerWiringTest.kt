package com.github.rocketraman.bootable.scheduler.db

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.startPlan
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.flyway.flywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.sqliteConfigModule
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
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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

/**
 * Starts the services in their planned order, as Bootable does, checks the migrated scheduler runs the tasks, and
 * shuts them down.
 */
private fun Set<AppService>.startAndRun(record: TaskRecord, client: SchedulerClient) {
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

    test("metro") {
      val graph = createGraph<TestGraph>()
      assert(graph.health.checks.single() is DbSchedulerHealthCheck)
      graph.appServices.startAndRun(graph.record, graph.schedulerClient)
    }
  }
}
