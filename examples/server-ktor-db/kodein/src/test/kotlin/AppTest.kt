import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.db.jdbc.JdbcConfig
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.testSuite
import org.kodein.di.direct
import org.kodein.di.instance
import javax.sql.DataSource

/** The graph resolves; nothing is started, so no database is created. */
val AppTest by testSuite {
  test("wires the database, schema, and server") {
    val di = appDI().direct
    val services = di.instance<Set<AppService>>()

    assert(services.map { it::class }.toSet() == setOf(JdbcDataSourceService::class, EntrySchema::class, EntryServer::class))
    assert(di.instance<JdbcConfig>() is SqliteConfig)
    assert(di.instance<DataSource>() is HikariDataSource)
  }
}
