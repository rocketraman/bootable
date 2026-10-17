import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.ConfigBinderProvider
import com.github.rocketraman.bootable.config.config
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import javax.sql.DataSource

/** This application's own DI scope — Bootable declares and uses its own [BootScope]. */
sealed interface AppScope

/** This application's own bindings: the server and what it uses, contributing its services to the boot set. */
@ContributesTo(AppScope::class)
interface EntriesBindings : ConfigBinderProvider {
  @Provides fun serverConfig(): ServerConfig = config("server")

  @Provides @SingleIn(AppScope::class)
  fun entryRepository(dataSource: DataSource): EntryRepository = EntryRepository(dataSource)

  @Provides @IntoSet fun entrySchema(dataSource: DataSource): AppService = EntrySchema(dataSource)

  @Provides @IntoSet
  fun entryServer(config: ServerConfig, entries: EntryRepository): AppService = EntryServer(config, entries, "metro")
}

/**
 * Metro wiring of the database-backed server. The `-metro` database modules contribute everything the database
 * needs: the pool's lifecycle from `boot-db-jdbc`, and the two choices, a driver config (SQLite, from `db.jdbc`) and a
 * pool (HikariCP, from `db.pool`). Leaving out either choice fails compilation with a missing binding.
 */
@DependencyGraph(AppScope::class, additionalScopes = [BootScope::class])
interface AppGraph : BootableGraph

fun main() = boot {
  createGraph<AppGraph>().bootable
}
