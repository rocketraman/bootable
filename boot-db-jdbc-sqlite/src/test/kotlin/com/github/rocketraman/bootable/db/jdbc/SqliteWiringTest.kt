package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.hopliteConfigModule
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance
import javax.sql.DataSource
import kotlin.time.Duration.Companion.seconds

sealed interface TestScope

@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class])
interface TestGraph : BootableGraph {
  val dataSource: DataSource
  val jdbcConfig: JdbcConfig
}

private fun JdbcDataSourceService.startAndStop() {
  start()
  try {
    dataSource.connection.use { connection ->
      connection.createStatement().use { it.executeQuery("SELECT 1").use { rs -> rs.next() } }
    }
  } finally {
    shutdown()
  }
}

/** Config comes from src/test/resources/application.conf, as it would in an application. */
val SqliteWiringTest by testSuite {
  testSuite("typed config decoded by") {
    test("hoplite") {
      val config = hopliteConfigBinder(report = false).config<SqliteConfig>("db")
      assert(
        config == SqliteConfig(
          path = "build/wiring-test.db",
          synchronous = SqliteSynchronous.NORMAL,
          busyTimeout = 2.seconds,
          pool = PoolConfig(
            poolName = "wiring-test",
            connectionTimeout = 3.seconds,
            properties = mapOf("leakDetectionThreshold" to "10000"),
          ),
        )
      )
      val hikari = hikariConfig(config)
      assert(hikari.leakDetectionThreshold == 10_000L)
      assert(hikari.connectionTimeout == 3_000L)
    }

    test("kodein") {
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(jdbcDataSourceModule)
        import(sqliteConfigModule())
      }.direct

      assert((di.instance<JdbcConfig>() as SqliteConfig).path == "build/wiring-test.db")
      val service = di.instance<Set<AppService>>().filterIsInstance<JdbcDataSourceService>().single()
      assert(service.dataSource === di.instance<DataSource>())
      service.startAndStop()
    }

    test("metro") {
      val graph = createGraph<TestGraph>()

      assert((graph.jdbcConfig as SqliteConfig).path == "build/wiring-test.db")
      val service = graph.appServices.filterIsInstance<JdbcDataSourceService>().single()
      assert(service.dataSource === graph.dataSource)
      service.startAndStop()
    }
  }
}
