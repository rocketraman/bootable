package com.github.rocketraman.bootable.db.flyway

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.db.jdbc.HikariPoolConfig
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.sqliteConfigModule
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton
import javax.sql.DataSource
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists

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

/** Starts the services in priority order, as Bootable does, checks the migrated schema, and shuts them down. */
private fun Set<AppService>.startAndStop() {
  // a fresh database each time, so the migration runs
  listOf("", "-wal", "-shm").forEach { Path("build/flyway-wiring-test.db$it").deleteIfExists() }
  val ordered = sortedByDescending { it.priority() }
  assert(ordered.map { it::class } == listOf(JdbcDataSourceService::class, FlywayMigrationService::class))
  ordered.forEach { it.start() }
  try {
    val dataSource = filterIsInstance<JdbcDataSourceService>().single().dataSource
    assert(dataSource.query("SELECT text FROM greeting WHERE id = 1") == listOf("hello from config"))
    assert(dataSource.query("SELECT count(*) FROM schema_history") == listOf("1"))
  } finally {
    ordered.reversed().forEach { it.shutdown() }
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

    test("metro") {
      val graph = createGraph<TestGraph>()

      val service = graph.appServices.filterIsInstance<FlywayMigrationService>().single()
      assert(service.flyway.configuration.installedBy == "metro")
      assert(service.flyway.configuration.dataSource === graph.dataSource)
      graph.appServices.startAndStop()
    }
  }
}
