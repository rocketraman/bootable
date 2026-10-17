import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.db.exposed.ExposedConfig
import com.github.rocketraman.bootable.db.exposed.SchemaVerificationService
import com.github.rocketraman.bootable.db.exposed.exposedDatabase
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
 * Bootable starts the services by priority: the pool first (validating a connection), then the migrations, then the
 * schema verification, then the server. Shutdown runs in reverse, so the server stops taking requests before the
 * pool closes.
 */
fun appServices(binder: ConfigBinder): Set<AppService> {
  val database = JdbcDataSourceService(
    binder.config<SqliteConfig>("db.jdbc"),
    HikariConnectionPoolFactory(binder.config("db.pool")),
  )
  val exposedConfig = binder.config<ExposedConfig>("db.exposed")
  val db = exposedDatabase(database.dataSource, exposedConfig)
  return setOf(
    database,
    FlywayMigrationService(database.dataSource, binder.config("db.flyway")),
    SchemaVerificationService(db, setOf(EntryTable), exposedConfig.schemaVerification),
    EntryServer(binder.config("server"), EntryRepository(db), "vanilla"),
  )
}

fun main() = boot {
  Bootable(appServices = appServices(hopliteConfigBinder()))
}
