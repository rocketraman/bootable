package snippets.database

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.db.exposed.ExposedConfig
import com.github.rocketraman.bootable.db.exposed.ExposedDatabaseService
import com.github.rocketraman.bootable.db.exposed.SchemaVerificationService
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.jdbc.HikariConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig

fun main() = boot {
  val binder = hopliteConfigBinder()
  // tag::wire[]
  val database = JdbcDataSourceService(
    binder.config<SqliteConfig>("db.jdbc"),                  // the driver's config
    HikariConnectionPoolFactory(binder.config("db.pool")),   // the pool
  )
  val exposedConfig = binder.config<ExposedConfig>("db.exposed")
  val exposed = ExposedDatabaseService(database.dataSource, exposedConfig)
  val notes = NoteRepository(exposed.database)

  Bootable(
    setOf(
      database,
      FlywayMigrationService(database.dataSource, binder.config("db.flyway")),
      exposed,
      SchemaVerificationService(exposed.database, setOf(NoteTable), exposedConfig.schemaVerification),
      // ... and the services that use `notes`
    ),
  )
  // end::wire[]
}
