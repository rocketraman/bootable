package com.github.rocketraman.bootable.scheduler.db

import com.github.kagkarlsson.scheduler.event.AbstractSchedulerListener
import com.github.kagkarlsson.scheduler.jdbc.PostgreSqlJdbcCustomization
import com.github.kagkarlsson.scheduler.jdbc.SqliteJdbcCustomization
import com.github.kagkarlsson.scheduler.task.ExecutionComplete
import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.HealthStatus
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.startPlan
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.jdbc.HikariConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.testSuite
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.time.Clock
import kotlin.time.Instant

val DbSchedulerServiceTest by testSuite {
  suspend fun withSqlite(block: suspend (DataSource, String) -> Unit) = withTempDir { dir ->
    val config = SqliteConfig(path = dir.resolve("scheduler.db").toString())
    withMigratedDatabase(config, "sqlite") { block(it, config.jdbcUrl) }
  }

  test("runs recurring and one-time tasks on SQLite, migrated by the shipped migration") {
    withSqlite { dataSource, _ -> checkTasksRun(dataSource) }
  }

  test("the migration is repeatable, and leaves an existing table alone") {
    withSqlite { dataSource, _ ->
      checkTasksRun(dataSource)
      // a second application start migrates nothing
      FlywayMigrationService(
        dataSource,
        com.github.rocketraman.bootable.db.flyway.FlywayConfig(locations = listOf("classpath:bootable/db-scheduler/sqlite")),
      ).start()
      assert(dataSource.taskNames() == listOf("heartbeat", "report"))
    }
  }

  test("executions survive a restart") {
    withSqlite { dataSource, _ ->
      val first = TaskRecord()
      val stopped = DbSchedulerService(dataSource, testSchedulerConfig, first.tasks)
      val later = Clock.System.now() + kotlin.time.Duration.parse("1h")
      stopped.scheduler.schedule(first.reminder.instance("later", Reminder("ada", "later")), later)
      // a client may schedule before the scheduler starts, or without it
      assert(dataSource.taskNames() == listOf("reminder"))

      val second = TaskRecord()
      val clock = object : Clock {
        override fun now(): Instant = later
      }
      val service = DbSchedulerService(dataSource, testSchedulerConfig, second.tasks, clock = clock)
      service.start()
      try {
        eventually { second.reminders == listOf(Reminder("ada", "later")) }
      } finally {
        service.shutdown()
      }
    }
  }

  test("creating the service opens no connection") {
    withTempDir { dir ->
      val pool = JdbcDataSourceService(SqliteConfig(path = dir.resolve("lazy.db").toString()), HikariConnectionPoolFactory())
      try {
        DbSchedulerService(pool.dataSource, testSchedulerConfig, TaskRecord().tasks)
        assert((pool.dataSource as HikariDataSource).hikariPoolMXBean == null)
      } finally {
        pool.shutdown()
      }
    }
  }

  test("the dialect matches the JDBC URL") {
    val ds = HikariDataSource()
    assert(jdbcCustomization(DbSchedulerDialect.Auto, true, ds, "jdbc:sqlite:x.db") is SqliteJdbcCustomization)
    assert(jdbcCustomization(DbSchedulerDialect.Auto, true, ds, "jdbc:postgresql://h/db") is PostgreSqlJdbcCustomization)
    assert(jdbcCustomization(DbSchedulerDialect.Auto, true, ds, "jdbc:h2:mem:") is LazyJdbcCustomization)
    assert(jdbcCustomization(DbSchedulerDialect.Sqlite, true, ds, "jdbc:postgresql://h/db") is SqliteJdbcCustomization)
    assert(dialectOf("jdbc:mysql://h/db") == DbSchedulerDialect.MySql8)
    assert(dialectOf("jdbc:mariadb://h/db") == DbSchedulerDialect.MariaDb)
  }

  test("without a JDBC URL, detects the database when the scheduler first uses it") {
    withSqlite { dataSource, _ ->
      val customization = jdbcCustomization(DbSchedulerDialect.Auto, true, dataSource, null)
      assert(customization.name == "SQLite")
    }
  }

  test("runs with the dialect picked from the URL") {
    withSqlite { dataSource, url ->
      checkTasksRun(dataSource, url)
    }
  }

  test("the customizer runs after the typed settings") {
    withSqlite { dataSource, _ ->
      val completions = AtomicInteger()
      val record = TaskRecord()
      val service = DbSchedulerService(dataSource, testSchedulerConfig, listOf(record.recurring), customizer = {
        it.addSchedulerListener(object : AbstractSchedulerListener() {
          override fun onExecutionComplete(executionComplete: ExecutionComplete) {
            completions.incrementAndGet()
          }
        })
      })
      service.start()
      try {
        eventually { completions.get() >= 2 }
      } finally {
        service.shutdown()
      }
    }
  }

  test("the health check is healthy while the scheduler runs") {
    withSqlite { dataSource, _ ->
      val service = DbSchedulerService(dataSource, testSchedulerConfig)
      val check = DbSchedulerHealthCheck(service.scheduler)
      assert(check.check() == HealthStatus.Unhealthy("not started"))
      service.start()
      assert(check.check() == HealthStatus.Healthy)
      service.shutdown()
      assert(check.check() == HealthStatus.Unhealthy("shutting down"))
    }
  }

  test("starts after the pool and the migrations, in the Serving phase") {
    withTempDir { dir ->
      val pool = JdbcDataSourceService(SqliteConfig(path = dir.resolve("plan.db").toString()), HikariConnectionPoolFactory())
      try {
        val migrations = FlywayMigrationService(pool.dataSource)
        val scheduler = DbSchedulerService(pool.dataSource)
        val app = object : AppService {}
        val plan = startPlan(setOf(scheduler, app, migrations, pool), tiebreaker = StartOrder.Tiebreaker.Shuffled())
        assert(plan == listOf(pool, migrations, app, scheduler))
      } finally {
        pool.shutdown()
      }
    }
  }
}
