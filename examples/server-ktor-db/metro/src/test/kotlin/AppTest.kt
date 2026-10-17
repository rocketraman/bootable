import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.createGraph

/** The graph resolves; nothing is started, so no database is created. */
val AppTest by testSuite {
  test("wires the database, schema, and server") {
    val services = createGraph<AppGraph>().appServices

    assert(services.map { it::class }.toSet() == setOf(JdbcDataSourceService::class, EntrySchema::class, EntryServer::class))
    assert(services.filterIsInstance<JdbcDataSourceService>().single().dataSource is HikariDataSource)
  }
}
