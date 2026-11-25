import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.client.http.ktor.koin.httpClientTelemetryModule
import com.github.rocketraman.bootable.client.http.ktor.koin.ktorClientModule
import com.github.rocketraman.bootable.config.koin.config
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import com.github.rocketraman.bootable.db.exposed.koin.exposedDatabaseModule
import com.github.rocketraman.bootable.db.flyway.koin.flywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.koin.hikariPoolMetricsModule
import com.github.rocketraman.bootable.db.jdbc.koin.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.koin.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.koin.jdbcHealthCheckModule
import com.github.rocketraman.bootable.db.jdbc.koin.jdbcTracingModule
import com.github.rocketraman.bootable.db.jdbc.koin.sqliteConfigModule
import com.github.rocketraman.bootable.leader.k8s.koin.kubernetesLeaderElectionModule
import com.github.rocketraman.bootable.periodic.PeriodicConfig
import com.github.rocketraman.bootable.periodic.PeriodicServices
import com.github.rocketraman.bootable.periodic.koin.periodicModule
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerTaskProvider
import com.github.rocketraman.bootable.scheduler.db.koin.dbSchedulerModule
import com.github.rocketraman.bootable.server.http.ktor.koin.ktorHealthModule
import com.github.rocketraman.bootable.server.http.ktor.koin.ktorServerCioEngineModule
import com.github.rocketraman.bootable.server.http.ktor.koin.ktorTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.koin.prometheusTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.koin.serviceStartTracingModule
import org.jetbrains.exposed.v1.core.Table
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * This application's own definitions: the server and what it uses, adding its services to the app services, and its
 * table to the tables whose schema is verified. Koin has no set multibindings, so each of those has a qualifier of its
 * own, and adds to the others rather than overriding them.
 */
val entriesModule = module {
  config<ServerConfig>("server")
  single { EntryRepository(get()) }
  single<Table>(named<EntryTable>()) { EntryTable }
  single<AppService>(named<SeedEntriesService>()) { SeedEntriesService(get()) }
  single { EntryCountReport(get()) }
  // the entry digest, every entries.digest.interval, once across the instances
  config<EntryDigestConfig>("entries.digest")
  single<DbSchedulerTaskProvider>(named<EntryDigest>()) { EntryDigest(get(), get()) }
  // the entry count, every entries.report.interval, on the leader only
  config<PeriodicConfig>("entries.report", named("entry-count"))
  single<AppService>(named("entry-count")) {
    get<PeriodicServices>().create("entry-count", get(named("entry-count"))) {
      get<EntryCountReport>().report()
    }
  }
  single<AppService>(named<EntryServer>()) {
    EntryServer(get(), get(), get(), get(), get<KtorClientService>()["entries"], "koin")
  }
  // the HTTP clients' engine; a test defines one that returns Ktor's MockEngine instead
  single<KtorClientEngine> { CioClientEngine() }
}

/**
 * Koin wiring of the database-backed server. The database is the pool's lifecycle from `boot-db-jdbc` plus the two
 * choices: a driver config (`sqliteConfigModule()`, from `db.jdbc`) and a pool (`hikariPoolModule()`, from `db.pool`),
 * whose metrics go through OpenTelemetry (`hikariPoolMetricsModule()`).
 * On top of the pool, `flywayMigrationModule()` migrates the schema (`db.flyway`), and `exposedDatabaseModule()` provides
 * the Exposed `Database` and verifies the schema against the defined tables (`db.exposed`). Telemetry goes through
 * the application's OpenTelemetry SDK (`openTelemetryModule()`), exposed for Prometheus (`prometheusTelemetryModule()`),
 * the server traces and measures its requests and serves the scrape (`ktorTelemetryModule()`), and each query is a span
 * within its request's trace, as is each connection taken from the pool (`jdbcTracingModule`). The application's start
 * is one trace, with a span for each service's start (`serviceStartTracingModule()`), so the queries of the migrations
 * and the sample entries are within those.
 * `periodicModule()` creates the loop that logs the entry count, which only the leader runs, of the instances elected
 * through a Kubernetes Lease (`kubernetesLeaderElectionModule()`, from `leader.kubernetes`), and, since there is an
 * `OpenTelemetry`, each run is a trace.
 * `dbSchedulerModule()` runs db-scheduler (`db.scheduler`), with the recurring task that logs the entry digest, over the
 * pool, and its table migrated by Flyway along with the application's, and, since there is an `OpenTelemetry`, each
 * execution is a trace.
 * `bootableModule()` tracks the application's health from its lifecycle, which the server serves (`ktorHealthModule()`),
 * with readiness down while the database doesn't answer (`jdbcHealthCheckModule()`).
 * The health probes and the scrape can each have a listener of their own instead, at `health.ktor.hostPort` and
 * `telemetry.ktor.hostPort`, which `ktorHealthModule()` and `ktorTelemetryModule()` add, on the server's engine
 * (`ktorServerCioEngineModule()`).
 * The HTTP clients (`ktorClientModule()`, from `http.client`) run on CIO, and each of their requests is a client span
 * within its caller's trace (`httpClientTelemetryModule()`).
 *
 * It is a Koin application of its own, rather than Koin's global one.
 */
fun appKoin(): KoinApplication = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    openTelemetryModule(),
    serviceStartTracingModule(),
    prometheusTelemetryModule(),
    ktorTelemetryModule(),
    ktorHealthModule(),
    ktorServerCioEngineModule(),
    ktorClientModule(),
    httpClientTelemetryModule(),
    jdbcDataSourceModule(),
    jdbcHealthCheckModule(),
    // a span for each connection taken from the pool too, which shows the pool's start and its first connection in
    // the application's start
    jdbcTracingModule { setDataSourceInstrumenterEnabled(true) },
    sqliteConfigModule(),
    hikariPoolModule(),
    hikariPoolMetricsModule(),
    flywayMigrationModule(),
    exposedDatabaseModule(),
    kubernetesLeaderElectionModule(),
    periodicModule(),
    dbSchedulerModule(),
    entriesModule,
  )
}

fun main() = boot {
  appKoin().koin.get<Bootable>()
}
