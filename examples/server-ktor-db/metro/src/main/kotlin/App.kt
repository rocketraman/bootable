import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.config.ConfigBinderProvider
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.jdbc.JdbcHealthCheckBindings
import com.github.rocketraman.bootable.db.jdbc.JdbcTelemetryConfigurer
import com.github.rocketraman.bootable.periodic.PeriodicServices
import com.github.kagkarlsson.scheduler.task.Task
import com.github.rocketraman.bootable.server.http.ktor.KtorHealth
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.telemetry.otel.ServiceStartTracingBindings
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import io.ktor.server.cio.*
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database

/** This application's own DI scope — Bootable declares and uses its own [BootScope]. */
sealed interface AppScope

/**
 * This application's own bindings: the server and what it uses, contributing its services to the boot set, and its
 * table to the set whose schema is verified.
 */
@ContributesTo(AppScope::class)
interface EntriesBindings : ConfigBinderProvider {
  @Provides fun serverConfig(): ServerConfig = config("server")

  @Provides @SingleIn(AppScope::class)
  fun entryRepository(db: Database): EntryRepository = EntryRepository(db)

  @Provides @IntoSet fun entryTable(): Table = EntryTable

  @Provides @IntoSet
  fun seedEntries(entries: EntryRepository): AppService = SeedEntriesService(entries)

  /** The entry count, every `entries.report.interval`, on the leader only. */
  @Provides @IntoSet
  fun entryCountLoop(periodic: PeriodicServices, entries: EntryRepository): AppService =
    periodic.create("entry-count", config("entries.report")) { EntryCountReport(entries).report() }

  /** The entry digest, a recurring db-scheduler task, every `entries.digest.interval`, once across the instances. */
  @Provides @IntoSet
  fun entryDigest(entries: EntryRepository): Task<*> = EntryDigest(entries, config("entries.digest")).task

  @Provides @IntoSet
  fun entryServer(
    config: ServerConfig,
    entries: EntryRepository,
    telemetry: KtorTelemetry,
    health: KtorHealth,
    clients: KtorClientService,
  ): AppService = EntryServer(config, entries, telemetry, health, clients["entries"], "metro")

  /** The HTTP clients' engine; a test provides one that returns Ktor's MockEngine instead. */
  @Provides fun ktorClientEngine(): KtorClientEngine = CioClientEngine()

  /**
   * A span for each connection taken from the pool too, as well as each query, which shows the pool's start and its
   * first connection in the application's start.
   */
  @Provides
  fun jdbcTelemetryConfigurer(): JdbcTelemetryConfigurer =
    JdbcTelemetryConfigurer { setDataSourceInstrumenterEnabled(true) }

  /** The scrape's own listener when `telemetry.ktor.hostPort` is set; otherwise it does nothing. */
  @Provides @IntoSet fun metricsListener(telemetry: KtorTelemetry): AppService = telemetry.listener(CIO)

  /** The health probes' own listener when `health.ktor.hostPort` is set; otherwise it does nothing. */
  @Provides @IntoSet fun healthListener(health: KtorHealth): AppService = health.listener(CIO)
}

/**
 * Metro wiring of the database-backed server. The `-metro` database modules contribute everything the database
 * needs: the pool's lifecycle from `boot-db-jdbc`, and the two choices, a driver config (SQLite, from `db.jdbc`) and a
 * pool (HikariCP, from `db.pool`). Leaving out either choice fails compilation with a missing binding. On top of the
 * pool, `boot-db-flyway-metro` migrates the schema (`db.flyway`), and `boot-db-exposed-metro` provides the Exposed
 * `Database` and verifies the schema against the contributed tables (`db.exposed`). The `-metro` telemetry modules
 * contribute the application's OpenTelemetry SDK, its Prometheus export, the Ktor instrumentation the server
 * installs, and the pool's metrics (`boot-db-pool-hikari-otel-metro`). `boot-db-jdbc-otel-metro` makes each query a
 * span within its request's trace, as is each connection taken from the pool (`JdbcTelemetryConfigurer`).
 * `boot-periodic-metro` creates the loop that logs the entry count, which only the leader runs, of the instances
 * elected through a Kubernetes Lease (`boot-leader-k8s-metro`, from `leader.kubernetes`), and each run is a trace
 * (`boot-periodic-otel-metro`).
 * `boot-scheduler-db-metro` runs db-scheduler (`db.scheduler`), with the recurring task that logs the entry digest,
 * over the pool, and its table migrated by Flyway along with the application's.
 * `boot-metro` tracks the application's health from its lifecycle, which the server serves
 * (`boot-server-http-ktor-metro`), with readiness down while the database doesn't answer (`JdbcHealthCheckBindings`).
 * The HTTP clients (`boot-client-http-ktor-metro`, from `http.client`) run on CIO, and
 * `boot-client-http-ktor-otel-metro` makes each of their requests a client span within its caller's trace.
 */
@DependencyGraph(
  AppScope::class,
  additionalScopes = [BootScope::class],
  // the start is one trace, with a span for each service's start, and the queries of the migrations and the sample
  // entries within those, rather than a trace each
  // readiness is down while the database doesn't answer
  bindingContainers = [ServiceStartTracingBindings::class, JdbcHealthCheckBindings::class],
)
interface AppGraph : BootableGraph

fun main() = boot {
  createGraph<AppGraph>().bootable
}
