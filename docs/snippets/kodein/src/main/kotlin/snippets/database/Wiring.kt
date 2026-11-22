package snippets.database

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.db.exposed.exposedDatabaseModule
import com.github.rocketraman.bootable.db.flyway.flywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.sqliteConfigModule
import org.jetbrains.exposed.v1.core.Table
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.new
import org.kodein.di.singleton

// tag::wire[]
fun appDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(jdbcDataSourceModule)   // the pool's lifecycle
  import(sqliteConfigModule)     // the driver's config, from db.jdbc
  import(hikariPoolModule)       // the pool, from db.pool
  import(flywayMigrationModule)  // migrations, from db.flyway
  import(exposedDatabaseModule)  // the Exposed Database, from db.exposed

  inBindSet<Table> { add { singleton { NoteTable } } }  // verified against the schema
  bindSingleton { new(::NoteRepository) }
  // ... and the services that use NoteRepository
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
// end::wire[]
