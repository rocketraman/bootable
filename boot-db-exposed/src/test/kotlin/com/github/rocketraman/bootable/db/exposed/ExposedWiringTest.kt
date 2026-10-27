package com.github.rocketraman.bootable.db.exposed

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.startPlan
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.db.flyway.FlywayConfig
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.flyway.flywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.HikariPoolConfig
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import com.github.rocketraman.bootable.db.jdbc.TransactionIsolation
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.sqliteConfigModule
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton
import java.sql.Connection
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.time.Duration.Companion.seconds

sealed interface TestScope

@DependencyGraph(
  TestScope::class,
  additionalScopes = [BootScope::class],
)
interface TestGraph : BootableGraph {
  val database: Database
}

@BindingContainer
@ContributesTo(TestScope::class)
object TestTableBindings {
  @Provides @IntoSet fun greetingTable(): Table = GreetingTable

  @Provides @IntoSet fun noteTable(): Table = NoteTable

  @Provides @IntoSet fun sqlLogger(): ExposedConfigCustomizer = ExposedConfigCustomizer { it.sqlLogger = StdOutSqlLogger }
}

/**
 * Starts the services in their planned order, as Bootable does, so Flyway migrates a fresh database and the schema
 * verification passes, then uses the database and shuts the services down.
 */
private suspend fun Set<AppService>.startUseAndStop(db: Database) {
  listOf("", "-wal", "-shm").forEach { Path("build/exposed-wiring-test.db$it").deleteIfExists() }
  // shuffled, so the order holds because the services declare it, not by accident
  val ordered = startPlan(this, tiebreaker = StartOrder.Tiebreaker.Shuffled())
  assert(
    ordered.map { it::class } ==
      listOf(
        JdbcDataSourceService::class,
        ExposedDatabaseService::class,
        FlywayMigrationService::class,
        SchemaVerificationService::class,
      )
  )
  ordered.forEach { it.start() }
  try {
    db.withTransaction { GreetingTable.insert { it[id] = 1; it[text] = "hello" } }
    assert(db.withTransaction { GreetingTable.selectAll().single()[GreetingTable.text] } == "hello")
  } finally {
    ordered.asReversed().forEach { it.shutdown() }
  }
  assert(runCatching { TransactionManager.managerFor(db) }.isFailure)
}

/** Config comes from src/test/resources/application.conf, as it would in an application. */
val ExposedWiringTest by testSuite {
  testSuite("exposed config decoded by") {
    test("hoplite") {
      val config = hopliteConfigBinder(report = false).config<ExposedConfig>("db.exposed")
      assert(
        config == ExposedConfig(
          defaultIsolationLevel = TransactionIsolation.SERIALIZABLE,
          defaultMaxAttempts = 1,
          warnLongQueriesDuration = 2.seconds,
          schemaVerification = SchemaVerification.FAIL,
        )
      )
    }

    test("hoplite, as all defaults when the section is absent") {
      assert(hopliteConfigBinder(report = false).config<ExposedConfig>("absent.exposed") == ExposedConfig())
    }

    test("hoplite in strict mode, with every db module's section side by side") {
      val binder = hopliteConfigBinder(report = false, strict = true)
      binder.config<SqliteConfig>("db.jdbc")
      binder.config<HikariPoolConfig>("db.pool")
      binder.config<FlywayConfig>("db.flyway")
      binder.config<ExposedConfig>("db.exposed")
    }

    test("kodein") {
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(jdbcDataSourceModule)
        import(sqliteConfigModule)
        import(hikariPoolModule)
        import(flywayMigrationModule)
        import(exposedDatabaseModule)
        inBindSet<Table> {
          add { singleton { GreetingTable } }
          add { singleton { NoteTable } }
        }
        inBindSet<ExposedConfigCustomizer> {
          add { singleton { ExposedConfigCustomizer { it.sqlLogger = StdOutSqlLogger } } }
        }
      }.direct

      val db = di.instance<Database>()
      assert(db.config.defaultIsolationLevel == Connection.TRANSACTION_SERIALIZABLE)
      assert(db.config.defaultMaxAttempts == 1)
      assert(db.config.sqlLogger === StdOutSqlLogger)
      di.instance<Set<AppService>>().startUseAndStop(db)
    }

    test("metro") {
      val graph = createGraph<TestGraph>()

      val db = graph.database
      assert(db.config.defaultIsolationLevel == Connection.TRANSACTION_SERIALIZABLE)
      assert(db.config.defaultMaxAttempts == 1)
      assert(db.config.sqlLogger === StdOutSqlLogger)
      graph.appServices.startUseAndStop(db)
    }
  }
}
