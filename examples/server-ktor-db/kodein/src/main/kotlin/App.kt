import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.bindAppService
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.db.exposed.exposedDatabaseModule
import com.github.rocketraman.bootable.db.flyway.flywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.hikariPoolMetricsModule
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.sqliteConfigModule
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.server.http.ktor.ktorTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.prometheusTelemetryModule
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
  bindAppService { singleton { EntryServer(instance(), instance(), instance(), "kodein") } }
  // the scrape's own listener when telemetry.ktor.hostPort is set; otherwise it does nothing
  bindAppService { singleton { instance<KtorTelemetry>().listener(CIO) } }
}

/**
 * Kodein wiring of the database-backed server. The database is the pool's lifecycle from `boot-db-jdbc` plus the two
 * choices: a driver config (`sqliteConfigModule`, from `db.jdbc`) and a pool (`hikariPoolModule`, from `db.pool`),
 * whose metrics go through OpenTelemetry (`hikariPoolMetricsModule`).
 * On top of the pool, `flywayMigrationModule` migrates the schema (`db.flyway`), and `exposedDatabaseModule` provides
 * the Exposed `Database` and verifies the schema against the registered tables (`db.exposed`). Telemetry goes through
 * the application's OpenTelemetry SDK (`openTelemetryModule`), exposed for Prometheus (`prometheusTelemetryModule`),
 * and the server traces and measures its requests and serves the scrape (`ktorTelemetryModule`).
 */
fun appDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(openTelemetryModule)
  import(prometheusTelemetryModule)
  import(ktorTelemetryModule)
  import(jdbcDataSourceModule)
  import(sqliteConfigModule)
  import(hikariPoolModule)
  import(hikariPoolMetricsModule)
  import(flywayMigrationModule)
  import(exposedDatabaseModule)
  import(entriesModule)
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
