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
import com.github.rocketraman.bootable.db.exposed.ExposedDatabaseService
import com.github.rocketraman.bootable.db.exposed.withTransaction
import com.github.rocketraman.bootable.db.jdbc.koin.hikariPoolModule as koinHikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.koin.jdbcDataSourceModule as koinJdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.koin.jdbcGenericConfigModule as koinJdbcGenericConfigModule
import com.github.rocketraman.bootable.db.jdbc.koin.jdbcTracingModule as koinJdbcTracingModule
import com.github.rocketraman.bootable.telemetry.otel.MetricsConfig
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizer
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryCustomizers
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.TelemetryConfig
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule as koinOpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.withSpan
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetryBuilder
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** Collects every span the SDK ends, in memory, as a test's window onto it. */
class InMemorySpans : OpenTelemetryCustomizer {
  val exporter: InMemorySpanExporter = InMemorySpanExporter.create()

  override fun customizeTracerProvider(builder: SdkTracerProviderBuilder) {
    builder.addSpanProcessor(SimpleSpanProcessor.create(exporter))
  }

  val spans: List<SpanData> get() = exporter.finishedSpanItems
}

private object EntryTable : Table("entry") {
  val name = text("name")
  val value = text("value")
}

private const val SECRET = "s3cret"

private val DB_STATEMENT = AttributeKey.stringKey("db.statement")
private val DB_SYSTEM = AttributeKey.stringKey("db.system")

private fun DataSource.createEntries() {
  connection.use { connection ->
    connection.createStatement().use {
      it.execute("CREATE TABLE IF NOT EXISTS entry (name TEXT PRIMARY KEY, value TEXT NOT NULL)")
      it.execute("INSERT OR REPLACE INTO entry VALUES ('a', 'A')")
    }
  }
}

private fun DataSource.selectSecret() {
  connection.use { connection ->
    connection.createStatement().use { statement ->
      statement.executeQuery("SELECT value FROM entry WHERE name = '$SECRET'").use { it.next() }
    }
  }
}

/** The client span of the one statement among [this] whose query text contains [text]. */
private fun List<SpanData>.query(text: String): SpanData =
  single { span -> span.kind == SpanKind.CLIENT && span.attributes.asMap().values.any { "$it".contains(text) } }

/** Runs [block] in a span named `parent`, and checks the query span among [spans] is its child. */
private suspend fun OpenTelemetry.checkQueryUnderSpan(spans: InMemorySpans, block: suspend () -> Unit) {
  getTracer("test").withSpan("parent") { block() }
  val parent = spans.spans.single { it.name == "parent" }
  val query = spans.spans.query("FROM entry WHERE")
  assert(query.parentSpanId == parent.spanId)
  assert(query.traceId == parent.traceId)
}

private fun telemetry(spans: InMemorySpans) = OpenTelemetryService(
  TelemetryConfig(metrics = MetricsConfig(runtime = false), registerGlobal = false),
  listOf(spans),
)

private suspend fun withTracedDatabase(
  dir: Path,
  configure: JdbcTelemetryBuilder.() -> Unit = {},
  block: suspend (JdbcDataSourceService, OpenTelemetry, InMemorySpans) -> Unit,
) {
  val spans = InMemorySpans()
  val telemetry = telemetry(spans)
  val database = JdbcDataSourceService(
    JdbcGenericConfig(jdbcUrl = "jdbc:sqlite:${dir.resolve("test.db")}"),
    HikariConnectionPoolFactory(),
    jdbcTracing(telemetry.openTelemetry, configure),
  )
  try {
    database.start()
    database.dataSource.createEntries()
    spans.exporter.reset()
    block(database, telemetry.openTelemetry, spans)
  } finally {
    database.shutdown()
    telemetry.shutdown()
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
  val openTelemetry: OpenTelemetry
  val inMemorySpans: InMemorySpans
}

sealed interface ConfiguredTestScope

@DependencyGraph(
  ConfiguredTestScope::class,
  additionalScopes = [BootScope::class],
  bindingContainers = [JdbcGenericConfigBindings::class, ConfiguredTestBindings::class],
)
interface ConfiguredTestGraph : BootableGraph {
  val dataSource: DataSource
  val inMemorySpans: InMemorySpans
}

@BindingContainer
object ConfiguredTestBindings {
  @Provides @SingleIn(ConfiguredTestScope::class) fun inMemorySpans(): InMemorySpans = InMemorySpans()

  @Provides @IntoSet fun inMemory(spans: InMemorySpans): OpenTelemetryCustomizer = spans

  @Provides fun jdbcTelemetryConfigurer(): JdbcTelemetryConfigurer = JdbcTelemetryConfigurer { setQuerySanitizationEnabled(false) }
}

@BindingContainer
object TestBindings {
  @Provides @SingleIn(TestScope::class) fun inMemorySpans(): InMemorySpans = InMemorySpans()

  @Provides @IntoSet fun inMemory(spans: InMemorySpans): OpenTelemetryCustomizer = spans
}

@DIModule(composed = true)
interface InaraTestModule :
  ConfigModule,
  BootableModule,
  OpenTelemetryModule,
  JdbcDataSourceModule,
  HikariPoolModule,
  JdbcTracingModule {
  @OpenTelemetryCustomizers val inMemorySpans: InMemorySpans
}

// a call resolves to the Kodein flavor's jdbcTracingModule, of the same signature; a reference of this type to Inara's
private val inaraJdbcTracingModule: (JdbcTelemetryBuilder.() -> Unit) -> JdbcTracingModulePart = ::jdbcTracingModule

private fun inaraTestModule(configure: JdbcTelemetryBuilder.() -> Unit = {}) = InaraTestModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),
  jdbcDataSourceModule = jdbcDataSourceModule(JdbcGenericConfigSource),
  hikariPoolModule = hikariPoolModule(),
  jdbcTracingModule = inaraJdbcTracingModule(configure),
  inMemorySpans = Binding.single { InMemorySpans() },
)

/** Starts [services], runs [block], and shuts them down in reverse. */
private suspend fun Set<AppService>.running(block: suspend () -> Unit) {
  val ordered = sortedBy { if (it is OpenTelemetryService) 0 else 1 }
  ordered.forEach { it.start() }
  try {
    block()
  } finally {
    ordered.asReversed().forEach { it.shutdown() }
  }
}

/** Config comes from src/test/resources/application.conf, as it would in an application. */
val JdbcTracingTest by testSuite {
  testSuite("jdbcTracing") {
    test("a query is a client span under the current span, with its query text sanitized") {
      withTempDir { dir ->
        withTracedDatabase(dir) { database, openTelemetry, spans ->
          // on another thread, as a blocking call from a coroutine would be
          openTelemetry.checkQueryUnderSpan(spans) {
            withContext(Dispatchers.IO) { database.dataSource.selectSecret() }
          }
          val query = spans.spans.query("FROM entry WHERE")
          // named for the operation, the database, and the table
          assert(query.name == "SELECT test.db.entry")
          // the semantic conventions' older attributes, until OTEL_SEMCONV_STABILITY_OPT_IN=database opts into the
          // stable ones, such as db.query.text
          assert(query.attributes.get(DB_STATEMENT) == "SELECT value FROM entry WHERE name = ?")
          assert(query.attributes.get(DB_SYSTEM) == "sqlite")
          assert(query.attributes.asMap().values.none { "$it".contains(SECRET) })
        }
      }
    }

    test("an Exposed transaction's queries are under the span its caller is in") {
      withTempDir { dir ->
        withTracedDatabase(dir) { database, openTelemetry, spans ->
          val db = ExposedDatabaseService(database.dataSource).database
          openTelemetry.checkQueryUnderSpan(spans) {
            // withTransaction runs the statements on its own dispatcher, Dispatchers.IO
            db.withTransaction { EntryTable.selectAll().where { EntryTable.name eq SECRET }.toList() }
          }
          assert(spans.spans.none { it.attributes.asMap().values.any { value -> "$value".contains(SECRET) } })
        }
      }
    }

    test("a query outside any span starts a trace of its own") {
      withTempDir { dir ->
        withTracedDatabase(dir) { database, _, spans ->
          database.dataSource.selectSecret()
          assert(!spans.spans.query("FROM entry WHERE").parentSpanContext.isValid)
        }
      }
    }

    test("configure changes the instrumentation's defaults") {
      withTempDir { dir ->
        withTracedDatabase(dir, configure = { setQuerySanitizationEnabled(false) }) { database, _, spans ->
          database.dataSource.selectSecret()
          assert(spans.spans.query("FROM entry WHERE").attributes.get(DB_STATEMENT)!!.contains(SECRET))
        }
      }
    }
  }

  testSuite("wired by") {
    test("kodein") {
      val spans = InMemorySpans()
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(openTelemetryModule)
        inBindSet<OpenTelemetryCustomizer> { add { singleton { spans } } }
        import(jdbcDataSourceModule)
        import(jdbcGenericConfigModule)
        import(hikariPoolModule)
        import(jdbcTracingModule())
      }.direct

      assert(di.instance<Set<DataSourceWrapper>>().size == 1)
      val dataSource = di.instance<DataSource>()
      di.instance<Set<AppService>>().running {
        dataSource.createEntries()
        di.instance<OpenTelemetry>().checkQueryUnderSpan(spans) {
          withContext(Dispatchers.IO) { dataSource.selectSecret() }
        }
      }
    }

    test("kodein, configured") {
      val spans = InMemorySpans()
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(openTelemetryModule)
        inBindSet<OpenTelemetryCustomizer> { add { singleton { spans } } }
        import(jdbcDataSourceModule)
        import(jdbcGenericConfigModule)
        import(hikariPoolModule)
        import(jdbcTracingModule { setQuerySanitizationEnabled(false) })
      }.direct

      val dataSource = di.instance<DataSource>()
      di.instance<Set<AppService>>().running {
        dataSource.createEntries()
        dataSource.selectSecret()
        // within running: shutting the SDK down clears the exporter
        assert(spans.spans.query("FROM entry WHERE").attributes.get(DB_STATEMENT)!!.contains(SECRET))
      }
    }

    test("koin") {
      val spans = InMemorySpans()
      val koin = koinApplication {
        modules(
          koinBootableModule(),
          koinHopliteConfigModule(),
          koinOpenTelemetryModule(),
          koinJdbcDataSourceModule(),
          koinJdbcGenericConfigModule(),
          koinHikariPoolModule(),
          koinJdbcTracingModule(),
          module { single<OpenTelemetryCustomizer>(named("test")) { spans } },
        )
      }.koin

      try {
        assert(koin.getAll<DataSourceWrapper>().size == 1)
        val dataSource = koin.get<DataSource>()
        koin.getAll<AppService>().toSet().running {
          dataSource.createEntries()
          koin.get<OpenTelemetry>().checkQueryUnderSpan(spans) {
            withContext(Dispatchers.IO) { dataSource.selectSecret() }
          }
        }
      } finally {
        koin.close()
      }
    }

    test("koin, configured") {
      val spans = InMemorySpans()
      val koin = koinApplication {
        modules(
          koinBootableModule(),
          koinHopliteConfigModule(),
          koinOpenTelemetryModule(),
          koinJdbcDataSourceModule(),
          koinJdbcGenericConfigModule(),
          koinHikariPoolModule(),
          koinJdbcTracingModule { setQuerySanitizationEnabled(false) },
          module { single<OpenTelemetryCustomizer>(named("test")) { spans } },
        )
      }.koin

      try {
        val dataSource = koin.get<DataSource>()
        koin.getAll<AppService>().toSet().running {
          dataSource.createEntries()
          dataSource.selectSecret()
          // within running: shutting the SDK down clears the exporter
          assert(spans.spans.query("FROM entry WHERE").attributes.get(DB_STATEMENT)!!.contains(SECRET))
        }
      } finally {
        koin.close()
      }
    }

    test("metro, configured") {
      val graph = createGraph<ConfiguredTestGraph>()
      graph.appServices.running {
        graph.dataSource.createEntries()
        graph.dataSource.selectSecret()
        assert(graph.inMemorySpans.spans.query("FROM entry WHERE").attributes.get(DB_STATEMENT)!!.contains(SECRET))
      }
    }

    test("metro") {
      val graph = createGraph<TestGraph>()
      graph.appServices.running {
        graph.dataSource.createEntries()
        graph.openTelemetry.checkQueryUnderSpan(graph.inMemorySpans) {
          withContext(Dispatchers.IO) { graph.dataSource.selectSecret() }
        }
      }
    }

    test("inara") {
      val module = inaraTestModule()

      assert(module.setOfDataSourceWrappers().size == 1)
      module.setOfAppServices().running {
        module.dataSource.createEntries()
        module.openTelemetry.checkQueryUnderSpan(module.inMemorySpans) {
          withContext(Dispatchers.IO) { module.dataSource.selectSecret() }
        }
      }
    }

    test("inara, configured") {
      val module = inaraTestModule { setQuerySanitizationEnabled(false) }
      module.setOfAppServices().running {
        module.dataSource.createEntries()
        module.dataSource.selectSecret()
        assert(module.inMemorySpans.spans.query("FROM entry WHERE").attributes.get(DB_STATEMENT)!!.contains(SECRET))
      }
    }
  }
}

@OptIn(ExperimentalPathApi::class)
private suspend fun withTempDir(block: suspend (Path) -> Unit) {
  val dir = Files.createTempDirectory("boot-db-jdbc-otel")
  try {
    block(dir)
  } finally {
    dir.deleteRecursively()
  }
}
