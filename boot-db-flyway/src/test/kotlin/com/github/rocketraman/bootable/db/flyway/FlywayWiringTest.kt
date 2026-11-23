@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.db.flyway

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
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
import com.github.rocketraman.bootable.db.flyway.koin.flywayMigrationModule as koinFlywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.HikariPoolConfig
import com.github.rocketraman.bootable.db.jdbc.HikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import com.github.rocketraman.bootable.db.jdbc.SqliteConfigSource
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.koin.hikariPoolModule as koinHikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.koin.jdbcDataSourceModule as koinJdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.koin.sqliteConfigModule as koinSqliteConfigModule
import com.github.rocketraman.bootable.db.jdbc.sqliteConfigModule
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.inara.DIModule
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton
import javax.sql.DataSource
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

sealed interface TestScope

@DependencyGraph(
  TestScope::class,
  additionalScopes = [BootScope::class],
  // named rather than contributed: a @ContributesTo in test code trips the power-assert bug KT-83931 on the hint
  // file Metro generates for it
  bindingContainers = [TestCustomizerBindings::class],
)
interface TestGraph : BootableGraph {
  val dataSource: DataSource
}

@BindingContainer
object TestCustomizerBindings {
  @Provides @IntoSet
  fun installedBy(): FlywayConfigurationCustomizer = FlywayConfigurationCustomizer { it.installedBy("metro") }
}

@DIModule(composed = true)
interface InaraTestModule : ConfigModule, BootableModule, JdbcDataSourceModule, HikariPoolModule, FlywayMigrationModule

private fun inaraTestModule() = InaraTestModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  jdbcDataSourceModule = jdbcDataSourceModule(SqliteConfigSource),
  hikariPoolModule = hikariPoolModule(),
  flywayMigrationModule = flywayMigrationModule(setOf(FlywayConfigurationCustomizer { it.installedBy("inara") })),
)

/** Starts the services in their planned order, as Bootable does, checks the migrated schema, and shuts them down. */
private suspend fun Set<AppService>.startAndStop() {
  // a fresh database each time, so the migration runs
  listOf("", "-wal", "-shm").forEach { Path("build/flyway-wiring-test.db$it").deleteIfExists() }
  // shuffled, so the order holds because the services declare it, not by accident
  val ordered = startPlan(this, tiebreaker = StartOrder.Tiebreaker.Shuffled())
  assert(ordered.map { it::class } == listOf(JdbcDataSourceService::class, FlywayMigrationService::class))
  ordered.forEach { it.start() }
  try {
    val dataSource = filterIsInstance<JdbcDataSourceService>().single().dataSource
    assert(dataSource.query("SELECT text FROM greeting WHERE id = 1") == listOf("hello from config"))
    assert(dataSource.query("SELECT count(*) FROM schema_history") == listOf("1"))
  } finally {
    ordered.asReversed().forEach { it.shutdown() }
  }
}

/** Config comes from src/test/resources/application.conf, as it would in an application. */
val FlywayWiringTest by testSuite {
  testSuite("flyway config decoded by") {
    test("hoplite") {
      val config = hopliteConfigBinder(report = false).config<FlywayConfig>("db.flyway")
      assert(
        config == FlywayConfig(
          locations = listOf("classpath:db/placeholder"),
          table = "schema_history",
          failOnMissingLocations = true,
          placeholders = mapOf("greeting" to "hello from config"),
        )
      )
    }

    test("hoplite, as all defaults when the section is absent") {
      assert(hopliteConfigBinder(report = false).config<FlywayConfig>("absent.flyway") == FlywayConfig())
    }

    test("hoplite in strict mode, alongside the driver and pool config") {
      val binder = hopliteConfigBinder(report = false, strict = true)
      binder.config<SqliteConfig>("db.jdbc")
      binder.config<HikariPoolConfig>("db.pool")
      binder.config<FlywayConfig>("db.flyway")
    }

    test("kodein") {
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(jdbcDataSourceModule)
        import(sqliteConfigModule)
        import(hikariPoolModule)
        import(flywayMigrationModule)
        inBindSet<FlywayConfigurationCustomizer> {
          add { singleton { FlywayConfigurationCustomizer { it.installedBy("kodein") } } }
        }
      }.direct

      assert(di.instance<FlywayMigrationService>().flyway.configuration.installedBy == "kodein")
      di.instance<Set<AppService>>().startAndStop()
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
          module {
            single<FlywayConfigurationCustomizer>(named("installedBy")) {
              FlywayConfigurationCustomizer { it.installedBy("koin") }
            }
          },
        )
      }.koin

      try {
        assert(koin.get<FlywayMigrationService>().flyway.configuration.installedBy == "koin")
        koin.getAll<AppService>().toSet().startAndStop()
      } finally {
        koin.close()
      }
    }

    test("metro") {
      val graph = createGraph<TestGraph>()

      val service = graph.appServices.filterIsInstance<FlywayMigrationService>().single()
      assert(service.flyway.configuration.installedBy == "metro")
      assert(service.flyway.configuration.dataSource === graph.dataSource)
      graph.appServices.startAndStop()
    }

    test("inara") {
      val module = inaraTestModule()

      val service = module.setOfAppServices().filterIsInstance<FlywayMigrationService>().single()
      assert(service.flyway.configuration.installedBy == "inara")
      assert(service.flyway.configuration.dataSource === module.dataSource)
      module.setOfAppServices().startAndStop()
    }
  }
}
