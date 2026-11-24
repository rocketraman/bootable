@file:Suppress("DEPRECATION")

// Kodein is deprecated: see the inara app, or the koin app for a DSL like Kodein's.

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.bindAppService
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.client.http.ktor.httpClientTelemetryModule
import com.github.rocketraman.bootable.client.http.ktor.ktorClientModule
import com.github.rocketraman.bootable.config.bindConfig
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
import com.github.rocketraman.bootable.periodic.PeriodicConfig
import com.github.rocketraman.bootable.periodic.PeriodicServices
import com.github.rocketraman.bootable.periodic.periodicModule
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerTaskProvider
import com.github.rocketraman.bootable.scheduler.db.dbSchedulerModule
import com.github.rocketraman.bootable.server.http.ktor.ktorServerCioEngineModule
import com.github.rocketraman.bootable.server.http.ktor.ktorHealthModule
import com.github.rocketraman.bootable.server.http.ktor.ktorTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.prometheusTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.serviceStartTracingModule
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
  bindConfig<ServerConfig>("server")
  bindSingleton { new(::EntryRepository) }
  inBindSet<Table> { add { singleton { EntryTable } } }
  bindAppService { singleton { new(::SeedEntriesService) } }
  bindSingleton { new(::EntryCountReport) }
  // the entry digest, every entries.digest.interval, once across the instances
  bindConfig<EntryDigestConfig>("entries.digest")
  inBindSet<DbSchedulerTaskProvider> { add { singleton { new(::EntryDigest) } } }
  // the entry count, every entries.report.interval, on the leader only
  bindConfig<PeriodicConfig>("entries.report", tag = "entry-count")
  bindAppService {
    singleton {
      instance<PeriodicServices>().create("entry-count", instance("entry-count")) {
        instance<EntryCountReport>().report()
      }
    }
  }
  bindAppService { singleton { EntryServer(instance(), instance(), instance(), instance(), instance<KtorClientService>()["entries"], "kodein") } }
  // the HTTP clients' engine; a test binds one that returns Ktor's MockEngine instead
  bindSingleton<KtorClientEngine> { CioClientEngine() }
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
 * through a Kubernetes Lease (`kubernetesLeaderElectionModule`, from `leader.kubernetes`), and, since there is an
 * `OpenTelemetry`, each run is a trace.
 * `dbSchedulerModule` runs db-scheduler (`db.scheduler`), with the recurring task that logs the entry digest, over the
 * pool, and its table migrated by Flyway along with the application's, and, since there is an `OpenTelemetry`, each
 * execution is a trace.
 * `bootableModule` tracks the application's health from its lifecycle, which the server serves (`ktorHealthModule`),
 * with readiness down while the database doesn't answer (`jdbcHealthCheckModule`).
 * The health probes and the scrape can each have a listener of their own instead, at `health.ktor.hostPort` and
 * `telemetry.ktor.hostPort`, which `ktorHealthModule` and `ktorTelemetryModule` add, on the server's engine
 * (`ktorServerCioEngineModule`).
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
  import(ktorServerCioEngineModule)
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
  import(dbSchedulerModule)
  import(entriesModule)
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
