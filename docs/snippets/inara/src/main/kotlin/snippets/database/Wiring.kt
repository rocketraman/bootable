package snippets.database

import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.db.exposed.ExposedDatabaseModule
import com.github.rocketraman.bootable.db.exposed.exposedDatabaseModule
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationModule
import com.github.rocketraman.bootable.db.flyway.flywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.HikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.SqliteConfigSource
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

// tag::wire[]
@DIModule(composed = true)
interface AppModule :
  ConfigModule, BootableModule, JdbcDataSourceModule, HikariPoolModule, FlywayMigrationModule, ExposedDatabaseModule {
  val noteRepository: NoteRepository
  // ... and the services that use NoteRepository
}

fun appModule() = AppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  // the pool's lifecycle, with the driver's config, from db.jdbc
  jdbcDataSourceModule = jdbcDataSourceModule(SqliteConfigSource),
  hikariPoolModule = hikariPoolModule(),            // the pool, from db.pool
  flywayMigrationModule = flywayMigrationModule(),  // migrations, from db.flyway
  // the Exposed Database, from db.exposed; its tables, a parameter, are verified against the schema
  exposedDatabaseModule = exposedDatabaseModule(setOf(NoteTable)),
  noteRepository = Binding.single { NoteRepository(database) },
)

fun main() = boot {
  appModule().bootable
}
// end::wire[]
