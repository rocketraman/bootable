import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.startPlan
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.db.exposed.ExposedDatabaseService
import com.github.rocketraman.bootable.db.exposed.SchemaVerificationService
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.testSuite

/** The wiring resolves; nothing is started, so no database is created. */
val AppTest by testSuite {
  test("wires the database, migrations, schema verification, seed entries, and server") {
    val services = appServices(hopliteConfigBinder(report = false))

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

  test("seeds the entries after the migrations, and before the server, in any tie order") {
    val services = appServices(hopliteConfigBinder(report = false))

    repeat(20) {
      val plan = startPlan(services, tiebreaker = StartOrder.Tiebreaker.Shuffled()).map { it::class }
      assert(plan.indexOf(FlywayMigrationService::class) < plan.indexOf(SeedEntriesService::class))
      assert(plan.indexOf(SeedEntriesService::class) < plan.indexOf(EntryServer::class))
    }
  }
}
