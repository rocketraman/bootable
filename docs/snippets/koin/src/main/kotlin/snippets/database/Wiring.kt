package snippets.database

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import com.github.rocketraman.bootable.db.exposed.koin.exposedDatabaseModule
import com.github.rocketraman.bootable.db.flyway.koin.flywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.koin.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.koin.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.koin.sqliteConfigModule
import org.jetbrains.exposed.v1.core.Table
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

// tag::wire[]
fun appKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    jdbcDataSourceModule(),   // the pool's lifecycle
    sqliteConfigModule(),     // the driver's config, from db.jdbc
    hikariPoolModule(),       // the pool, from db.pool
    flywayMigrationModule(),  // migrations, from db.flyway
    exposedDatabaseModule(),  // the Exposed Database, from db.exposed
    module {
      single<Table>(named<NoteTable>()) { NoteTable }  // verified against the schema
      single { NoteRepository(get()) }
      // ... and the services that use NoteRepository
    },
  )
}.koin

fun main() = boot {
  appKoin().get<Bootable>()
}
// end::wire[]
