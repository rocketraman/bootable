import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.LifecycleController
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.defaultLifecycleControllers
import com.github.rocketraman.bootable.boot.buildInfo
import com.github.rocketraman.bootable.boot.readinessConfig
import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.HttpClientTelemetry
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.db.exposed.ExposedConfig
import com.github.rocketraman.bootable.db.exposed.ExposedDatabaseService
import com.github.rocketraman.bootable.db.exposed.SchemaVerificationService
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.jdbc.HikariConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.JdbcHealthCheck
import com.github.rocketraman.bootable.db.jdbc.hikariPoolMetrics
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import com.github.rocketraman.bootable.db.jdbc.jdbcTracing
import com.github.rocketraman.bootable.leader.k8s.KubernetesLeaderElection
import com.github.rocketraman.bootable.leader.k8s.KubernetesLeaderElectionConfig
import com.github.rocketraman.bootable.leader.k8s.kubernetesClientDefaults
import com.github.rocketraman.bootable.periodic.PeriodicGate
import com.github.rocketraman.bootable.periodic.PeriodicServices
import com.github.rocketraman.bootable.periodic.periodicTracing
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerService
import com.github.rocketraman.bootable.scheduler.db.dbSchedulerTracing
import com.github.rocketraman.bootable.server.http.ktor.KtorHealth
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetryConfig
import com.github.rocketraman.bootable.server.http.ktor.excludeHealthFromTraces
import com.github.rocketraman.bootable.server.http.ktor.ktorHealthConfig
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.PrometheusTelemetry
import com.github.rocketraman.bootable.telemetry.otel.ServiceStartTracing
import com.github.rocketraman.bootable.telemetry.otel.telemetryConfig
import io.ktor.server.cio.*

/**
 * Vanilla wiring of a database-backed server: the two database choices are a driver config (`SqliteConfig`, from
 * `db.jdbc`) and a pool (`HikariConnectionPoolFactory`, configured from `db.pool`). Flyway migrates the pool's
 * `dataSource`, and the repository uses it through an Exposed `Database`, whose tables are verified against the
 * migrated schema.
 *
 * Telemetry goes through the application's `OpenTelemetryService`: the server traces and measures its requests, each
 * query is a span within its request's trace, the pool records its metrics, and the server serves the metrics for Prometheus to scrape (`telemetry.sdk`,
 * `telemetry.prometheus`, `telemetry.ktor`).
 *
 * Bootable starts the services phase by phase: the OpenTelemetry SDK first, then the pool (validating a connection)
 * and the HTTP clients, then the migrations, then the schema verification and the sample entries, then the server.
 * Shutdown runs in reverse, so the server stops taking requests before the Exposed `Database` is unregistered, and the
 * pool and the HTTP clients close, and the SDK exports the last telemetry, the pool's included. The set's order doesn't matter: each service declares
 * where it starts, and the start plan is logged.
 *
 * A periodic loop logs the entry count every `entries.report.interval`, on the leader only, of the instances elected
 * through a Kubernetes Lease (`leader.kubernetes`), and each run is a trace. db-scheduler (`db.scheduler`) runs a
 * recurring task that logs the entry digest, once across the instances, with no leader, its table migrated by Flyway
 * along with the application's.
 *
 * `HealthLifecycleController`, one of the lifecycle controllers, tracks the application's lifecycle, and the server
 * serves it: liveness, and readiness, which is up only while the application is running and the database answers
 * (`health.readiness`, `health.ktor`).
 */
class EntriesApp(binder: ConfigBinder) {
  val services: Set<AppService>
  val lifecycleControllers: Set<LifecycleController>

  init {
    val prometheus = PrometheusTelemetry(binder.config("telemetry.prometheus"))
    val ktorConfig = binder.config<KtorTelemetryConfig>("telemetry.ktor")
    val healthConfig = ktorHealthConfig(binder)
    val telemetry = OpenTelemetryService(
      telemetryConfig(binder),
      // neither the scrapes nor the health probes are traced
      listOf(prometheus, ktorConfig.excludeScrapeFromTraces(), healthConfig.excludeHealthFromTraces()),
    )
    val ktorTelemetry = KtorTelemetry(telemetry.openTelemetry, setOf(prometheus), ktorConfig)
    val leaderConfig = binder.config<KubernetesLeaderElectionConfig>("leader.kubernetes")
    // the HTTP clients, on CIO, each request a client span within its caller's trace (`http.client`,
    // `telemetry.httpClient`); in a cluster, the leader election's client trusts the API server by default
    val clients = KtorClientService(
      binder.config("http.client"),
      CioClientEngine(),
      HttpClientTelemetry(telemetry.openTelemetry, binder.config("telemetry.httpClient")),
      defaults = listOf(kubernetesClientDefaults(leaderConfig)),
    )

    val sqliteConfig = binder.config<SqliteConfig>("db.jdbc")
    val database = JdbcDataSourceService(
      sqliteConfig,
      // HikariCP records its pool metrics through OpenTelemetry
      HikariConnectionPoolFactory(binder.config("db.pool"), hikariPoolMetrics(telemetry.openTelemetry)),
      // each query is a span, under the request's, and so is each connection taken from the pool, which shows the
      // pool's start and its first connection in the application's start
      jdbcTracing(telemetry.openTelemetry) { setDataSourceInstrumenterEnabled(true) },
    )
    val exposedConfig = binder.config<ExposedConfig>("db.exposed")
    val exposed = ExposedDatabaseService(database.dataSource, exposedConfig)
    val repository = EntryRepository(exposed.database)
    // readiness is down while the database doesn't answer
    val health = HealthLifecycleController(setOf(JdbcHealthCheck(database.dataSource)), readinessConfig(binder))
    val ktorHealth = KtorHealth(health, healthConfig, buildInfo(binder))
    // the election calls the API server through the kubernetes client
    val leaderElection = KubernetesLeaderElection(leaderConfig, clients.getOrDefault(leaderConfig.httpClient))
    val periodic = PeriodicServices(
      PeriodicGate.leader(leaderElection),
      interceptors = listOf(periodicTracing(telemetry.openTelemetry)),
    )
    val entryCount = EntryCountReport(repository)
    services = setOf(
      telemetry,
      database,
      clients,
      exposed,
      FlywayMigrationService(database.dataSource, binder.config("db.flyway")),
      SchemaVerificationService(exposed.database, setOf(EntryTable), exposedConfig.schemaVerification),
      SeedEntriesService(repository),
      leaderElection,
      periodic.create("entry-count", binder.config("entries.report")) { entryCount.report() },
      DbSchedulerService(
        database.dataSource,
        binder.config("db.scheduler"),
        EntryDigest(repository, binder.config("entries.digest")).tasks(),
        // each execution a trace of its own
        customizer = dbSchedulerTracing(telemetry.openTelemetry),
        // db-scheduler's SQL dialect, from the JDBC URL
        jdbcUrl = sqliteConfig.jdbcUrl,
      ),
      EntryServer(binder.config("server"), repository, ktorTelemetry, ktorHealth, clients["entries"], "vanilla"),
      // the scrape's own listener when telemetry.ktor.hostPort is set; otherwise it does nothing
      ktorTelemetry.listener(CIO),
      // the health probes' own listener when health.ktor.hostPort is set; otherwise it does nothing
      ktorHealth.listener(CIO),
    )
    // the start is one trace, with a span for each service's start, and the queries of the migrations and the sample
    // entries within those, rather than a trace each
    lifecycleControllers = defaultLifecycleControllers() + ServiceStartTracing(telemetry.openTelemetry) + health
  }
}

fun main() = boot {
  val app = EntriesApp(hopliteConfigBinder())
  Bootable(app.services, app.lifecycleControllers)
}
