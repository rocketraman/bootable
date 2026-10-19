import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.bindAppService
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.db.exposed.exposedDatabaseModule
import com.github.rocketraman.bootable.db.flyway.flywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.sqliteConfigModule
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
  bindAppService { singleton { EntryServer(instance(), instance(), "kodein") } }
}

/**
 * Kodein wiring of the database-backed server. The database is the pool's lifecycle from `boot-db-jdbc` plus the two
 * choices: a driver config (`sqliteConfigModule`, from `db.jdbc`) and a pool (`hikariPoolModule`, from `db.pool`).
 * On top of the pool, `flywayMigrationModule` migrates the schema (`db.flyway`), and `exposedDatabaseModule` provides
 * the Exposed `Database` and verifies the schema against the registered tables (`db.exposed`).
 */
fun appDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(jdbcDataSourceModule)
  import(sqliteConfigModule)
  import(hikariPoolModule)
  import(flywayMigrationModule)
  import(exposedDatabaseModule)
  import(entriesModule)
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
