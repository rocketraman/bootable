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
import kotlin.time.Duration.Companion.seconds

sealed interface TestScope

@DependencyGraph(
  TestScope::class,
  additionalScopes = [BootScope::class],
  // named rather than contributed: a @ContributesTo in test code trips the power-assert bug KT-83931 on the hint
  // file Metro generates for it
  bindingContainers = [JdbcGenericConfigBindings::class, TestCustomizerBindings::class],
)
interface TestGraph : BootableGraph {
  val dataSource: DataSource
}

@BindingContainer
object TestCustomizerBindings {
  @Provides @IntoSet
  fun initSql(): HikariConfigCustomizer = HikariConfigCustomizer { it.connectionInitSql = "SELECT 'metro'" }
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
val HikariWiringTest by testSuite {
  testSuite("pool config decoded by") {
    test("hoplite") {
      val config = hopliteConfigBinder(report = false).config<HikariPoolConfig>("db.pool")
      assert(
        config == HikariPoolConfig(
          maximumPoolSize = 3,
          connectionTimeout = 3.seconds,
          poolName = "hikari-wiring-test",
          leakDetectionThreshold = 10.seconds,
          transactionIsolation = TransactionIsolation.SERIALIZABLE,
        )
      )
    }

    test("hoplite, as all defaults when the section is absent") {
      assert(hopliteConfigBinder(report = false).config<HikariPoolConfig>("absent.pool") == HikariPoolConfig())
    }

    test("hoplite in strict mode, alongside the driver config") {
      val binder = hopliteConfigBinder(report = false, strict = true)
      binder.config<JdbcGenericConfig>("db.jdbc")
      binder.config<HikariPoolConfig>("db.pool")
    }

    test("kodein") {
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(jdbcDataSourceModule)
        import(jdbcGenericConfigModule)
        import(hikariPoolModule)
        inBindSet<HikariConfigCustomizer> {
          add { singleton { HikariConfigCustomizer { it.connectionInitSql = "SELECT 'kodein'" } } }
        }
      }.direct

      val hikari = di.instance<DataSource>() as HikariDataSource
      assert(hikari.maximumPoolSize == 3)
      assert(hikari.poolName == "hikari-wiring-test")
      assert(hikari.transactionIsolation == "TRANSACTION_SERIALIZABLE")
      assert(hikari.connectionInitSql == "SELECT 'kodein'")
      di.instance<Set<AppService>>().filterIsInstance<JdbcDataSourceService>().single().startAndStop()
    }

    test("metro") {
      val graph = createGraph<TestGraph>()

      val hikari = graph.dataSource as HikariDataSource
      assert(hikari.maximumPoolSize == 3)
      assert(hikari.poolName == "hikari-wiring-test")
      assert(hikari.transactionIsolation == "TRANSACTION_SERIALIZABLE")
      assert(hikari.connectionInitSql == "SELECT 'metro'")
      graph.appServices.filterIsInstance<JdbcDataSourceService>().single().startAndStop()
    }
  }
}
