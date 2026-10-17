import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.db.jdbc.HikariConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig

/**
 * Vanilla wiring of a database-backed server: the two database choices are a driver config (`SqliteConfig`, from
 * `db.jdbc`) and a pool (`HikariConnectionPoolFactory`, configured from `db.pool`). Everything else uses the pool's
 * `dataSource`.
 *
 * Bootable starts the services by priority: the pool first (validating a connection), then the schema, then the
 * server. Shutdown runs in reverse, so the server stops taking requests before the pool closes.
 */
fun appServices(binder: ConfigBinder): Set<AppService> {
  val database = JdbcDataSourceService(
    binder.config<SqliteConfig>("db.jdbc"),
    HikariConnectionPoolFactory(binder.config("db.pool")),
  )
  return setOf(
    database,
    EntrySchema(database.dataSource),
    EntryServer(binder.config("server"), EntryRepository(database.dataSource), "vanilla"),
  )
}

fun main() = boot {
  Bootable(appServices = appServices(hopliteConfigBinder()))
}
