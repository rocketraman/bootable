import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.ConfigBinderProvider
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
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

  @Provides @IntoSet
  fun entryServer(config: ServerConfig, entries: EntryRepository, telemetry: KtorTelemetry): AppService =
    EntryServer(config, entries, telemetry, "metro")

  /** The scrape's own listener when `telemetry.ktor.hostPort` is set; otherwise it does nothing. */
  @Provides @IntoSet fun metricsListener(telemetry: KtorTelemetry): AppService = telemetry.listener(CIO)
}

/**
 * Metro wiring of the database-backed server. The `-metro` database modules contribute everything the database
 * needs: the pool's lifecycle from `boot-db-jdbc`, and the two choices, a driver config (SQLite, from `db.jdbc`) and a
 * pool (HikariCP, from `db.pool`). Leaving out either choice fails compilation with a missing binding. On top of the
 * pool, `boot-db-flyway-metro` migrates the schema (`db.flyway`), and `boot-db-exposed-metro` provides the Exposed
 * `Database` and verifies the schema against the contributed tables (`db.exposed`). The `-metro` telemetry modules
 * contribute the application's OpenTelemetry SDK, its Prometheus export, the Ktor instrumentation the server
 * installs, and the pool's metrics (`boot-db-pool-hikari-otel-metro`). `boot-db-jdbc-otel-metro` makes each query a
 * span within its request's trace.
 */
@DependencyGraph(AppScope::class, additionalScopes = [BootScope::class])
interface AppGraph : BootableGraph

fun main() = boot {
  createGraph<AppGraph>().bootable
}
