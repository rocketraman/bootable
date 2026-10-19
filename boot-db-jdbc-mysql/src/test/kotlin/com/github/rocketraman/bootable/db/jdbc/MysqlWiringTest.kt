package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.zaxxer.hikari.HikariDataSource
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

private val expectedConfig = MysqlConfig(
  host = "db.test",
  port = 3307,
  database = "app",
  username = "app",
  password = "secret",
  sslMode = MysqlSslMode.VERIFY_IDENTITY,
  connectTimeout = 3.seconds,
)

/**
 * The pool built from the decoded config. The server in the config doesn't exist, so nothing is started: the pool
 * opens no connection until then.
 */
private fun HikariDataSource.assertBuiltFromConfig() {
  assert(jdbcUrl == "jdbc:mysql://db.test:3307/app")
  assert(username == "app" && password == "secret")
  assert(dataSourceProperties.getProperty("sslMode") == "VERIFY_IDENTITY")
  assert(poolName == "wiring-test")
}

/** Config comes from src/test/resources/application.conf, as it would in an application. */
val MysqlWiringTest by testSuite {
  testSuite("typed config decoded by") {
    test("hoplite") {
      assert(hopliteConfigBinder(report = false).config<MysqlConfig>("db.jdbc") == expectedConfig)
    }

    test("hoplite in strict mode, alongside the pool config") {
      val binder = hopliteConfigBinder(report = false, strict = true)
      binder.config<MysqlConfig>("db.jdbc")
      binder.config<HikariPoolConfig>("db.pool")
    }

    test("kodein") {
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(jdbcDataSourceModule)
        import(mysqlConfigModule)
        import(hikariPoolModule)
      }.direct

      assert(di.instance<JdbcConfig>() == expectedConfig)
      val hikari = di.instance<DataSource>() as HikariDataSource
      hikari.assertBuiltFromConfig()
      assert(di.instance<Set<AppService>>().filterIsInstance<JdbcDataSourceService>().single().dataSource === hikari)
      hikari.close()
    }

    test("metro") {
      val graph = createGraph<TestGraph>()

      assert(graph.jdbcConfig == expectedConfig)
      val hikari = graph.dataSource as HikariDataSource
      hikari.assertBuiltFromConfig()
      assert(graph.appServices.filterIsInstance<JdbcDataSourceService>().single().dataSource === hikari)
      hikari.close()
    }
  }
}
