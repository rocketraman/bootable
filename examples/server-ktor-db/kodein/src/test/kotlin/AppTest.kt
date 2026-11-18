import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.db.exposed.ExposedDatabaseService
import com.github.rocketraman.bootable.db.exposed.SchemaVerificationService
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.jdbc.JdbcConfig
import com.github.rocketraman.bootable.boot.Health
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.JdbcHealthCheck
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import com.github.rocketraman.bootable.server.http.ktor.KtorHealth
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.server.cio.*
import javax.sql.DataSource
import org.jetbrains.exposed.v1.core.Table
import org.kodein.di.direct
import org.kodein.di.instance

private val noListener = KtorTelemetry(io.opentelemetry.api.OpenTelemetry.noop()).listener(CIO)
private val noHealthListener = KtorHealth(Health()).listener(CIO)

/** The graph resolves; nothing is started, so no database is created. */
val AppTest by testSuite {
  test("wires the telemetry, database, HTTP clients, migrations, schema verification, seed entries, and server") {
    val di = appDI().direct
    val services = di.instance<Set<AppService>>()

    assert(
      services.map { it::class }.toSet() == setOf(
        OpenTelemetryService::class,
        JdbcDataSourceService::class,
        KtorClientService::class,
        ExposedDatabaseService::class,
        FlywayMigrationService::class,
        SchemaVerificationService::class,
        SeedEntriesService::class,
        EntryServer::class,
        // the scrape's own listener, which does nothing unless telemetry.ktor.hostPort is set
        noListener::class,
        // the health probes' own listener, which does nothing unless health.ktor.hostPort is set
        noHealthListener::class,
      )
    )
    assert(di.instance<JdbcConfig>() is SqliteConfig)
    // the pool is HikariCP, under the JDBC tracing
    assert(di.instance<DataSource>().isWrapperFor(HikariDataSource::class.java))
    assert(di.instance<Set<Table>>() == setOf(EntryTable))
    // readiness is down while the database doesn't answer
    assert(di.instance<Health>().checks.single() is JdbcHealthCheck)
  }
}
