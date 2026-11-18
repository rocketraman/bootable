import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.bindAppService
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.client.http.ktor.httpClientTelemetryModule
import com.github.rocketraman.bootable.client.http.ktor.ktorClientModule
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.db.exposed.exposedDatabaseModule
import com.github.rocketraman.bootable.db.flyway.flywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.hikariPoolMetricsModule
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.jdbcHealthCheckModule
import com.github.rocketraman.bootable.db.jdbc.jdbcTracingModule
import com.github.rocketraman.bootable.db.jdbc.sqliteConfigModule
import com.github.rocketraman.bootable.leader.k8s.kubernetesLeaderElectionModule
import com.github.rocketraman.bootable.periodic.PeriodicServices
import com.github.rocketraman.bootable.periodic.periodicModule
import com.github.rocketraman.bootable.periodic.periodicTracingModule
import com.github.rocketraman.bootable.scheduler.db.dbSchedulerModule
import com.github.kagkarlsson.scheduler.task.Task
import com.github.rocketraman.bootable.server.http.ktor.KtorHealth
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.server.http.ktor.ktorHealthModule
import com.github.rocketraman.bootable.server.http.ktor.ktorTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.prometheusTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.serviceStartTracingModule
import io.ktor.server.cio.*
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.jetbrains.exposed.v1.core.Table
import org.kodein.di.direct
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.new
import org.kodein.di.singleton

/**
 * This application's own bindings: the server and what it uses, contributing its services to the boot set, and its
 * table to the set whose schema is verified.
 */
val entriesModule by DI.Module {
  bindSingleton { instance<ConfigBinder>().config<ServerConfig>("server") }
  bindSingleton { new(::EntryRepository) }
  inBindSet<Table> { add { singleton { EntryTable } } }
  bindAppService { singleton { new(::SeedEntriesService) } }
  bindSingleton { new(::EntryCountReport) }
  // the entry digest, every entries.digest.interval, once across the instances
  bindSingleton { instance<ConfigBinder>().config<EntryDigestConfig>("entries.digest") }
  inBindSet<Task<*>> { add { singleton { new(::EntryDigest).task } } }
  // the entry count, every entries.report.interval, on the leader only
  bindAppService {
    singleton {
      instance<PeriodicServices>().create("entry-count", instance<ConfigBinder>().config("entries.report")) {
        instance<EntryCountReport>().report()
      }
    }
  }
  bindAppService { singleton { EntryServer(instance(), instance(), instance(), instance(), instance<KtorClientService>()["entries"], "kodein") } }
  // the HTTP clients' engine; a test binds one that returns Ktor's MockEngine instead
  bindSingleton<KtorClientEngine> { CioClientEngine() }
  // the scrape's own listener when telemetry.ktor.hostPort is set; otherwise it does nothing
  bindAppService { singleton { instance<KtorTelemetry>().listener(CIO) } }
  // the health probes' own listener when health.ktor.hostPort is set; otherwise it does nothing
  bindAppService { singleton { instance<KtorHealth>().listener(CIO) } }
}

/**
 * Kodein wiring of the database-backed server. The database is the pool's lifecycle from `boot-db-jdbc` plus the two
 * choices: a driver config (`sqliteConfigModule`, from `db.jdbc`) and a pool (`hikariPoolModule`, from `db.pool`),
 * whose metrics go through OpenTelemetry (`hikariPoolMetricsModule`).
 * On top of the pool, `flywayMigrationModule` migrates the schema (`db.flyway`), and `exposedDatabaseModule` provides
 * the Exposed `Database` and verifies the schema against the registered tables (`db.exposed`). Telemetry goes through
 * the application's OpenTelemetry SDK (`openTelemetryModule`), exposed for Prometheus (`prometheusTelemetryModule`),
 * the server traces and measures its requests and serves the scrape (`ktorTelemetryModule`), and each query is a span
 * within its request's trace, as is each connection taken from the pool (`jdbcTracingModule`). The application's start is one trace, with a span for each
 * service's start (`serviceStartTracingModule`), so the queries of the migrations and the sample entries are within
 * those.
 * `periodicModule` creates the loop that logs the entry count, which only the leader runs, of the instances elected
 * through a Kubernetes Lease (`kubernetesLeaderElectionModule`, from `leader.kubernetes`), and each run is a trace
 * (`periodicTracingModule`).
 * `dbSchedulerModule` runs db-scheduler (`db.scheduler`), with the recurring task that logs the entry digest, over the
 * pool, and its table migrated by Flyway along with the application's.
 * `bootableModule` tracks the application's health from its lifecycle, which the server serves (`ktorHealthModule`),
 * with readiness down while the database doesn't answer (`jdbcHealthCheckModule`).
 * The HTTP clients (`ktorClientModule`, from `http.client`) run on CIO, and each of their requests is a client span
 * within its caller's trace (`httpClientTelemetryModule`).
 */
fun appDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(openTelemetryModule)
  import(serviceStartTracingModule)
  import(prometheusTelemetryModule)
  import(ktorTelemetryModule)
  import(ktorHealthModule)
  import(ktorClientModule)
  import(httpClientTelemetryModule)
  import(jdbcDataSourceModule)
  import(jdbcHealthCheckModule)
  // a span for each connection taken from the pool too, which shows the pool's start and its first connection in the
  // application's start
  import(jdbcTracingModule { setDataSourceInstrumenterEnabled(true) })
  import(sqliteConfigModule)
  import(hikariPoolModule)
  import(hikariPoolMetricsModule)
  import(flywayMigrationModule)
  import(exposedDatabaseModule)
  import(kubernetesLeaderElectionModule)
  import(periodicModule)
  import(periodicTracingModule())
  import(dbSchedulerModule)
  import(entriesModule)
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
