package snippets.database

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.db.exposed.ExposedDatabaseService
import com.github.rocketraman.bootable.db.exposed.SchemaVerificationService
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import de.infix.testBalloon.framework.core.testSuite
import org.kodein.di.direct
import org.kodein.di.instance

/** The snippet's graph resolves; nothing is started, so no database is created. */
val WiringTest by testSuite {
  test("wires the pool, migrations, Exposed, and schema verification") {
    val services = appDI().direct.instance<Set<AppService>>()
    assert(
      services.map { it::class }.toSet() == setOf(
        JdbcDataSourceService::class,
        FlywayMigrationService::class,
        ExposedDatabaseService::class,
        SchemaVerificationService::class,
      )
    )
  }
}
