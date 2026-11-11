import com.github.rocketraman.bootable.db.exposed.ExposedDatabaseService
import com.github.rocketraman.bootable.db.exposed.SchemaVerificationService
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.testSuite

/** The module resolves; nothing is started, so no database is created. */
val AppTest by testSuite {
  test("collects the database, migrations, schema verification, seed entries, and server services") {
    val app = appModule()
    val services = app.appServices

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
    // the composed module shares its parents' singletons with the services they contributed
    assert(services.filterIsInstance<JdbcDataSourceService>().single() === app.jdbcDataSourceService)
  }
}
