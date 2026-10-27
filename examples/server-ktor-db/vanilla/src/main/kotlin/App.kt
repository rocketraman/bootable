import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.db.exposed.ExposedConfig
import com.github.rocketraman.bootable.db.exposed.ExposedDatabaseService
import com.github.rocketraman.bootable.db.exposed.SchemaVerificationService
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.jdbc.HikariConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig

/**
 * Vanilla wiring of a database-backed server: the two database choices are a driver config (`SqliteConfig`, from
 * `db.jdbc`) and a pool (`HikariConnectionPoolFactory`, configured from `db.pool`). Flyway migrates the pool's
 * `dataSource`, and the repository uses it through an Exposed `Database`, whose tables are verified against the
 * migrated schema.
 *
 * Bootable starts the services phase by phase: the pool first (validating a connection), then the migrations, then
 * the schema verification and the sample entries, then the server. Shutdown runs in reverse, so the server stops
 * taking requests before the Exposed `Database` is unregistered and the pool closes. The set's order doesn't matter:
 * each service declares where it starts, and the start plan is logged.
 */
fun appServices(binder: ConfigBinder): Set<AppService> {
  val database = JdbcDataSourceService(
    binder.config<SqliteConfig>("db.jdbc"),
    HikariConnectionPoolFactory(binder.config("db.pool")),
  )
  val exposedConfig = binder.config<ExposedConfig>("db.exposed")
  val exposed = ExposedDatabaseService(database.dataSource, exposedConfig)
  val repository = EntryRepository(exposed.database)
  return setOf(
    database,
    exposed,
    FlywayMigrationService(database.dataSource, binder.config("db.flyway")),
    SchemaVerificationService(exposed.database, setOf(EntryTable), exposedConfig.schemaVerification),
    SeedEntriesService(repository),
    EntryServer(binder.config("server"), repository, "vanilla"),
  )
}

fun main() = boot {
  Bootable(appServices = appServices(hopliteConfigBinder()))
}
