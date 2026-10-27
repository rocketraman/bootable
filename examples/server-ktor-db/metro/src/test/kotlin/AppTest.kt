import com.github.rocketraman.bootable.db.exposed.ExposedDatabaseService
import com.github.rocketraman.bootable.db.exposed.SchemaVerificationService
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.createGraph

/** The graph resolves; nothing is started, so no database is created. */
val AppTest by testSuite {
  test("wires the database, migrations, schema verification, seed entries, and server") {
    val services = createGraph<AppGraph>().appServices

    assert(
      services.map { it::class }.toSet() == setOf(
        JdbcDataSourceService::class,
        ExposedDatabaseService::class,
        FlywayMigrationService::class,
        SchemaVerificationService::class,
        SeedEntriesService::class,
        EntryServer::class,
      )
    )
    assert(services.filterIsInstance<JdbcDataSourceService>().single().dataSource is HikariDataSource)
  }
}
