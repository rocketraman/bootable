import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.HttpClientTelemetryModule
import com.github.rocketraman.bootable.client.http.ktor.KtorClientModule
import com.github.rocketraman.bootable.client.http.ktor.httpClientTelemetryModule
import com.github.rocketraman.bootable.client.http.ktor.ktorClientModule
import com.github.rocketraman.bootable.clock.ClockModule
import com.github.rocketraman.bootable.clock.clockModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.exposed.ExposedDatabaseModule
import com.github.rocketraman.bootable.db.exposed.exposedDatabaseModule
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationModule
import com.github.rocketraman.bootable.db.flyway.flywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.HikariPoolMetricsModule
import com.github.rocketraman.bootable.db.jdbc.HikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.JdbcHealthCheckModule
import com.github.rocketraman.bootable.db.jdbc.JdbcTracingModule
import com.github.rocketraman.bootable.db.jdbc.SqliteConfigSource
import com.github.rocketraman.bootable.db.jdbc.hikariPoolMetricsModule
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.jdbcHealthCheckModule
import com.github.rocketraman.bootable.db.jdbc.jdbcTracingModule
import com.github.rocketraman.bootable.leader.k8s.KubernetesLeaderElectionModule
import com.github.rocketraman.bootable.leader.k8s.kubernetesLeaderElectionModule
import com.github.rocketraman.bootable.periodic.PeriodicAppService
import com.github.rocketraman.bootable.periodic.PeriodicModule
import com.github.rocketraman.bootable.periodic.periodicModule
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerModule
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerTaskProviders
import com.github.rocketraman.bootable.scheduler.db.dbSchedulerModule
import com.github.rocketraman.bootable.server.http.ktor.KtorHealthModule
import com.github.rocketraman.bootable.server.http.ktor.KtorServerEngineModule
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetryModule
import com.github.rocketraman.bootable.server.http.ktor.ktorHealthModule
import com.github.rocketraman.bootable.server.http.ktor.ktorServerCioEngineModule
import com.github.rocketraman.bootable.server.http.ktor.ktorTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.PrometheusTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.ServiceStartTracingModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.prometheusTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.serviceStartTracingModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * This application's own module: the server and what it uses, over the Bootable modules it requires. Its services are
 * among the application's, and its entry digest among db-scheduler's tasks.
 */
@DIModule
interface EntriesModule :
  @DIModule.Required ExposedDatabaseModule,
  @DIModule.Required KtorTelemetryModule,
  @DIModule.Required KtorHealthModule,
  @DIModule.Required KtorClientModule,
  @DIModule.Required PeriodicModule,
  @DIModule.Required DbSchedulerModule {
  val serverConfig: ServerConfig
  val entryRepository: EntryRepository
  @AppServices val seedEntriesService: SeedEntriesService
  val entryCountReport: EntryCountReport
  val entryDigestConfig: EntryDigestConfig
  @DbSchedulerTaskProviders val entryDigest: EntryDigest
  @AppServices val entryCountLoop: PeriodicAppService
  @AppServices val entryServer: EntryServer
}

/** An [EntriesModule], built like the Bootable modules. */
fun entriesModule(): EntriesModulePart = EntriesModulePart(
  serverConfig = config("server"),
  entryRepository = Binding.single { EntryRepository(database) },
  seedEntriesService = Binding.single { SeedEntriesService(entryRepository) },
  entryCountReport = Binding.single { EntryCountReport(entryRepository) },
  // the entry digest, every entries.digest.interval, once across the instances
  entryDigestConfig = config("entries.digest"),
  entryDigest = Binding.single { EntryDigest(entryRepository, entryDigestConfig) },
  // the entry count, every entries.report.interval, on the leader only
  entryCountLoop = Binding.single {
    periodicServices.create("entry-count", configBinder.config("entries.report")) { entryCountReport.report() }
  },
  entryServer = Binding.single {
    EntryServer(serverConfig, entryRepository, ktorTelemetry, ktorHealth, ktorClientService["entries"], "inara")
  },
)

/** The application's module: the Bootable modules it uses, and its own, composed. */
@DIModule(composed = true)
interface AppModule :
  ConfigModule,
  BootableModule,
  ClockModule,
  OpenTelemetryModule,
  ServiceStartTracingModule,
  PrometheusTelemetryModule,
  KtorServerEngineModule,
  KtorTelemetryModule,
  KtorHealthModule,
  KtorClientModule,
  HttpClientTelemetryModule,
  JdbcDataSourceModule,
  JdbcHealthCheckModule,
  JdbcTracingModule,
  HikariPoolModule,
  HikariPoolMetricsModule,
  FlywayMigrationModule,
  ExposedDatabaseModule,
  KubernetesLeaderElectionModule,
  PeriodicModule,
  DbSchedulerModule,
  EntriesModule

/**
 * Inara wiring of the database-backed server. Its config is Hoplite's `hopliteConfigModule()`, which every other
 * module reads, and `bootableModule()` runs the services they mark `@AppServices`, and tracks the application's health
 * from its lifecycle, which the server serves (`ktorHealthModule()`), with readiness down while the database doesn't
 * answer (`jdbcHealthCheckModule()`).
 *
 * The database is `jdbcDataSourceModule(…)` with a driver module's config (`SqliteConfigSource`, from `db.jdbc`), over
 * a pool (`hikariPoolModule()`, from `db.pool`), whose metrics go through OpenTelemetry (`hikariPoolMetricsModule()`).
 * On top of it, `flywayMigrationModule()` migrates the schema (`db.flyway`), and `exposedDatabaseModule(…)` provides
 * the Exposed `Database` and verifies the schema of the given tables (`db.exposed`).
 *
 * Telemetry goes through the application's OpenTelemetry SDK (`openTelemetryModule()`), exposed for Prometheus
 * (`prometheusTelemetryModule()`), the server traces and measures its requests and serves the scrape
 * (`ktorTelemetryModule()`), and each query is a span within its request's trace, as is each connection taken from the
 * pool (`jdbcTracingModule(…)`). The application's start is one trace, with a span for each service's start
 * (`serviceStartTracingModule()`).
 * The health probes and the scrape can each have a listener of their own instead, at `health.ktor.hostPort` and
 * `telemetry.ktor.hostPort`, which `ktorHealthModule()` and `ktorTelemetryModule()` add, on the server's engine
 * (`ktorServerCioEngineModule()`).
 *
 * `periodicModule()` creates the loop that logs the entry count, which only the leader runs, of the instances elected
 * through a Kubernetes Lease (`kubernetesLeaderElectionModule()`, from `leader.kubernetes`), and each run is a trace.
 * `dbSchedulerModule()` runs db-scheduler (`db.scheduler`), with the recurring task that logs the entry digest, over
 * the pool, and each execution is a trace. The HTTP clients (`ktorClientModule(…)`, from `http.client`) run on CIO, and
 * each of their requests is a client span within its caller's trace (`httpClientTelemetryModule()`).
 *
 * Each module requires the modules it uses, and the `AppModule` provides each one once, so they all share one config,
 * one `OpenTelemetry`, and one `DataSource`. A missing or duplicated module doesn't compile.
 */
fun appModule(): AppModule = AppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  clockModule = clockModule(),
  openTelemetryModule = openTelemetryModule(),
  serviceStartTracingModule = serviceStartTracingModule(),
  prometheusTelemetryModule = prometheusTelemetryModule(),
  // the server's engine, for the listeners of their own of the health probes and the scrape, when their hostPort is set
  ktorServerEngineModule = ktorServerCioEngineModule(),
  ktorTelemetryModule = ktorTelemetryModule(),
  ktorHealthModule = ktorHealthModule(),
  // the HTTP clients' engine; a test passes one that returns Ktor's MockEngine instead
  ktorClientModule = ktorClientModule(CioClientEngine()),
  httpClientTelemetryModule = httpClientTelemetryModule(),
  jdbcDataSourceModule = jdbcDataSourceModule(SqliteConfigSource),
  jdbcHealthCheckModule = jdbcHealthCheckModule(),
  // a span for each connection taken from the pool too, which shows the pool's start and its first connection in the
  // application's start
  jdbcTracingModule = jdbcTracingModule { setDataSourceInstrumenterEnabled(true) },
  hikariPoolModule = hikariPoolModule(),
  hikariPoolMetricsModule = hikariPoolMetricsModule(),
  flywayMigrationModule = flywayMigrationModule(),
  exposedDatabaseModule = exposedDatabaseModule(setOf(EntryTable)),
  kubernetesLeaderElectionModule = kubernetesLeaderElectionModule(),
  periodicModule = periodicModule(),
  dbSchedulerModule = dbSchedulerModule(),
  entriesModule = entriesModule(),
)

fun main() = boot {
  appModule().bootable
}
