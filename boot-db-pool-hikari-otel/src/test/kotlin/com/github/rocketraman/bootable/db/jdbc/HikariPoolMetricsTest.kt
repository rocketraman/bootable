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
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule as koinHopliteConfigModule
import com.github.rocketraman.bootable.db.jdbc.koin.hikariPoolMetricsModule as koinHikariPoolMetricsModule
import com.github.rocketraman.bootable.db.jdbc.koin.hikariPoolModule as koinHikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.koin.jdbcDataSourceModule as koinJdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.koin.jdbcGenericConfigModule as koinJdbcGenericConfigModule
import com.github.rocketraman.bootable.telemetry.otel.MetricsConfig
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizers
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.TelemetryConfig
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule as koinOpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.IMetricsTracker
import com.zaxxer.hikari.metrics.MetricsTrackerFactory
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** Collects every metric the SDK records, in memory, as a test's window onto it. */
class InMemoryMetrics : OpenTelemetryCustomizer {
  val reader: InMemoryMetricReader = InMemoryMetricReader.create()

  override fun customizeMeterProvider(builder: SdkMeterProviderBuilder) {
    builder.registerMetricReader(reader)
  }

  fun collect(): List<MetricData> = reader.collectAllMetrics().toList()
}

private val POOL_NAME = AttributeKey.stringKey("pool.name")

/** The pool's metrics, as OpenTelemetry's HikariCP instrumentation names them. */
private val POOL_METRICS = setOf(
  "db.client.connections.usage",
  "db.client.connections.max",
  "db.client.connections.idle.min",
  "db.client.connections.pending_requests",
  "db.client.connections.wait_time",
  "db.client.connections.use_time",
)

/** Starts the pool, uses a connection, and checks [metrics] has the pool's metrics, under the pool's name. */
private suspend fun JdbcDataSourceService.checkPoolMetrics(metrics: InMemoryMetrics, poolName: String) {
  start()
  try {
    dataSource.connection.use { connection ->
      connection.createStatement().use { it.executeQuery("SELECT 1").use { rs -> rs.next() } }
    }
    val collected = metrics.collect()
    val names = collected.map { it.name }.toSet()
    assert(names.containsAll(POOL_METRICS)) { "missing: ${POOL_METRICS - names}" }
    val usage = collected.single { it.name == "db.client.connections.usage" }
    assert(usage.data.points.all { it.attributes.get(POOL_NAME) == poolName })
  } finally {
    shutdown()
  }
}

sealed interface TestScope

@DependencyGraph(
  TestScope::class,
  additionalScopes = [BootScope::class],
  // named rather than contributed: a @ContributesTo in test code trips the power-assert bug KT-83931 on the hint
  // file Metro generates for it
  bindingContainers = [JdbcGenericConfigBindings::class, TestBindings::class],
)
interface TestGraph : BootableGraph {
  val dataSource: DataSource
  val inMemoryMetrics: InMemoryMetrics
}

@BindingContainer
object TestBindings {
  @Provides @SingleIn(TestScope::class) fun inMemoryMetrics(): InMemoryMetrics = InMemoryMetrics()

  @Provides @IntoSet fun inMemory(metrics: InMemoryMetrics): OpenTelemetryCustomizer = metrics

  @Provides @IntoSet
  fun initSql(): HikariConfigCustomizer = HikariConfigCustomizer { it.connectionInitSql = "SELECT 'metro'" }
}

@DIModule(composed = true)
interface InaraTestModule :
  ConfigModule,
  BootableModule,
  OpenTelemetryModule,
  JdbcDataSourceModule,
  HikariPoolModule,
  HikariPoolMetricsModule {
  @OpenTelemetryCustomizers val inMemoryMetrics: InMemoryMetrics
  @HikariConfigCustomizers val initSql: HikariConfigCustomizer
}

private fun inaraTestModule() = InaraTestModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),
  jdbcDataSourceModule = jdbcDataSourceModule(JdbcGenericConfigSource),
  hikariPoolModule = hikariPoolModule(),
  hikariPoolMetricsModule = hikariPoolMetricsModule(),
  inMemoryMetrics = Binding.single { InMemoryMetrics() },
  initSql = Binding.value(HikariConfigCustomizer { it.connectionInitSql = "SELECT 'inara'" }),
)

/** Config comes from src/test/resources/application.conf, as it would in an application. */
val HikariPoolMetricsTest by testSuite {
  testSuite("hikariPoolMetrics") {
    test("records the pool's metrics through the SDK") {
      val metrics = InMemoryMetrics()
      val telemetry = OpenTelemetryService(
        TelemetryConfig(metrics = MetricsConfig(runtime = false), registerGlobal = false),
        listOf(metrics),
      )
      try {
        val database = JdbcDataSourceService(
          JdbcGenericConfig(jdbcUrl = "jdbc:sqlite:build/hikari-pool-metrics-vanilla.db"),
          HikariConnectionPoolFactory(HikariPoolConfig(poolName = "vanilla"), hikariPoolMetrics(telemetry.openTelemetry)),
        )
        database.checkPoolMetrics(metrics, "vanilla")
      } finally {
        telemetry.shutdown()
      }
    }

    test("keeps a tracker factory set before it") {
      val metrics = InMemoryMetrics()
      val telemetry = OpenTelemetryService(
        TelemetryConfig(metrics = MetricsConfig(runtime = false), registerGlobal = false),
        listOf(metrics),
      )
      val acquired = AtomicInteger()
      val ownTracker = MetricsTrackerFactory { _, _ ->
        object : IMetricsTracker {
          override fun recordConnectionAcquiredNanos(elapsedAcquiredNanos: Long) {
            acquired.incrementAndGet()
          }
        }
      }
      val otel = hikariPoolMetrics(telemetry.openTelemetry)
      try {
        val database = JdbcDataSourceService(
          JdbcGenericConfig(jdbcUrl = "jdbc:sqlite:build/hikari-pool-metrics-own.db"),
          HikariConnectionPoolFactory(HikariPoolConfig(poolName = "own")) { config ->
            config.metricsTrackerFactory = ownTracker
            otel.customize(config)
          },
        )
        database.checkPoolMetrics(metrics, "own")
        assert(acquired.get() > 0)
      } finally {
        telemetry.shutdown()
      }
    }
  }

  testSuite("wired by") {
    test("kodein, alongside the application's own customizer") {
      val metrics = InMemoryMetrics()
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(openTelemetryModule)
        inBindSet<OpenTelemetryCustomizer> { add { singleton { metrics } } }
        import(jdbcDataSourceModule)
        import(jdbcGenericConfigModule)
        import(hikariPoolModule)
        import(hikariPoolMetricsModule)
        inBindSet<HikariConfigCustomizer> {
          add { singleton { HikariConfigCustomizer { it.connectionInitSql = "SELECT 'kodein'" } } }
        }
      }.direct

      assert(di.instance<Set<HikariConfigCustomizer>>().size == 2)
      val hikari = di.instance<DataSource>() as HikariDataSource
      assert(hikari.connectionInitSql == "SELECT 'kodein'")
      val services = di.instance<Set<AppService>>()
      try {
        services.filterIsInstance<JdbcDataSourceService>().single().checkPoolMetrics(metrics, "metrics-wiring-test")
      } finally {
        services.filterIsInstance<OpenTelemetryService>().single().shutdown()
      }
    }

    test("koin, alongside the application's own customizer") {
      val metrics = InMemoryMetrics()
      val koin = koinApplication {
        modules(
          koinBootableModule(),
          koinHopliteConfigModule(),
          koinOpenTelemetryModule(),
          koinJdbcDataSourceModule(),
          koinJdbcGenericConfigModule(),
          koinHikariPoolModule(),
          koinHikariPoolMetricsModule(),
          module {
            single<OpenTelemetryCustomizer>(named("test")) { metrics }
            single<HikariConfigCustomizer>(named("initSql")) {
              HikariConfigCustomizer { it.connectionInitSql = "SELECT 'koin'" }
            }
          },
        )
      }.koin

      try {
        assert(koin.getAll<HikariConfigCustomizer>().size == 2)
        val hikari = koin.get<DataSource>() as HikariDataSource
        assert(hikari.connectionInitSql == "SELECT 'koin'")
        val services = koin.getAll<AppService>()
        try {
          services.filterIsInstance<JdbcDataSourceService>().single().checkPoolMetrics(metrics, "metrics-wiring-test")
        } finally {
          services.filterIsInstance<OpenTelemetryService>().single().shutdown()
        }
      } finally {
        koin.close()
      }
    }

    test("metro, alongside the application's own customizer") {
      val graph = createGraph<TestGraph>()

      val hikari = graph.dataSource as HikariDataSource
      assert(hikari.connectionInitSql == "SELECT 'metro'")
      try {
        graph.appServices.filterIsInstance<JdbcDataSourceService>().single()
          .checkPoolMetrics(graph.inMemoryMetrics, "metrics-wiring-test")
      } finally {
        graph.appServices.filterIsInstance<OpenTelemetryService>().single().shutdown()
      }
    }

    test("inara, alongside the application's own customizer") {
      val module = inaraTestModule()

      assert(module.setOfHikariConfigCustomizers().size == 2)
      val hikari = module.dataSource as HikariDataSource
      assert(hikari.connectionInitSql == "SELECT 'inara'")
      val services = module.setOfAppServices()
      try {
        services.filterIsInstance<JdbcDataSourceService>().single()
          .checkPoolMetrics(module.inMemoryMetrics, "metrics-wiring-test")
      } finally {
        services.filterIsInstance<OpenTelemetryService>().single().shutdown()
      }
    }
  }
}
