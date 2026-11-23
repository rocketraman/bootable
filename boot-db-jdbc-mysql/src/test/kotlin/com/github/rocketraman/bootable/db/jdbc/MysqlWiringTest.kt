@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.koin.bootableModule as koinBootableModule
import com.github.rocketraman.bootable.boot.setOfAppServices
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.config.Masked
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule as koinHopliteConfigModule
import com.github.rocketraman.bootable.db.jdbc.koin.hikariPoolModule as koinHikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.koin.jdbcDataSourceModule as koinJdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.koin.mysqlConfigModule as koinMysqlConfigModule
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.inara.DIModule
import org.kodein.di.instance
import javax.sql.DataSource
import kotlin.time.Duration.Companion.seconds
import org.koin.dsl.koinApplication

sealed interface TestScope

@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class])
interface TestGraph : BootableGraph {
  val dataSource: DataSource
  val jdbcConfig: JdbcConfig
}

@DIModule(composed = true)
interface InaraTestModule : ConfigModule, BootableModule, JdbcDataSourceModule, HikariPoolModule

private fun inaraTestModule() = InaraTestModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  jdbcDataSourceModule = jdbcDataSourceModule(MysqlConfigSource),
  hikariPoolModule = hikariPoolModule(),
)

private val expectedConfig = MysqlConfig(
  host = "db.test",
  port = 3307,
  database = "app",
  username = "app",
  password = Masked("secret"),
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

    test("koin") {
      val koin = koinApplication {
        modules(
          koinBootableModule(),
          koinHopliteConfigModule(),
          koinJdbcDataSourceModule(),
          koinMysqlConfigModule(),
          koinHikariPoolModule(),
        )
      }.koin

      try {
        assert(koin.get<JdbcConfig>() == expectedConfig)
        val hikari = koin.get<DataSource>() as HikariDataSource
        hikari.assertBuiltFromConfig()
        assert(koin.getAll<AppService>().filterIsInstance<JdbcDataSourceService>().single().dataSource === hikari)
        hikari.close()
      } finally {
        koin.close()
      }
    }

    test("metro") {
      val graph = createGraph<TestGraph>()

      assert(graph.jdbcConfig == expectedConfig)
      val hikari = graph.dataSource as HikariDataSource
      hikari.assertBuiltFromConfig()
      assert(graph.appServices.filterIsInstance<JdbcDataSourceService>().single().dataSource === hikari)
      hikari.close()
    }

    test("inara") {
      val module = inaraTestModule()

      assert(module.jdbcConfig == expectedConfig)
      val hikari = module.dataSource as HikariDataSource
      hikari.assertBuiltFromConfig()
      assert(module.setOfAppServices().filterIsInstance<JdbcDataSourceService>().single().dataSource === hikari)
      hikari.close()
    }
  }
}
