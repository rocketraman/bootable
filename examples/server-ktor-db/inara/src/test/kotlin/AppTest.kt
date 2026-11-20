import com.github.rocketraman.bootable.boot.Health
import com.github.rocketraman.bootable.boot.setOfAppServices
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.db.exposed.ExposedDatabaseService
import com.github.rocketraman.bootable.db.exposed.SchemaVerificationService
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.JdbcHealthCheck
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import com.github.rocketraman.bootable.leader.k8s.KubernetesLeaderElection
import com.github.rocketraman.bootable.periodic.PeriodicAppService
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerService
import com.github.rocketraman.bootable.server.http.ktor.KtorHealth
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.server.cio.*

private val noListener = KtorTelemetry(io.opentelemetry.api.OpenTelemetry.noop()).listener(CIO)
private val noHealthListener = KtorHealth(Health()).listener(CIO)

/** The module resolves; nothing is started, so no database is created. */
val AppTest by testSuite {
  test("wires the telemetry, database, HTTP clients, migrations, schema verification, seed entries, and server") {
    val app = appModule()
    val services = app.setOfAppServices()

    assert(
      services.map { it::class }.toSet() == setOf(
        OpenTelemetryService::class,
        JdbcDataSourceService::class,
        KtorClientService::class,
        ExposedDatabaseService::class,
        FlywayMigrationService::class,
        SchemaVerificationService::class,
        SeedEntriesService::class,
        KubernetesLeaderElection::class,
        PeriodicAppService::class,
        DbSchedulerService::class,
        EntryServer::class,
        // the scrape's own listener, which does nothing unless telemetry.ktor.hostPort is set
        noListener::class,
        // the health probes' own listener, which does nothing unless health.ktor.hostPort is set
        noHealthListener::class,
      )
    )
    assert(app.jdbcConfig is SqliteConfig)
    // the pool is HikariCP, under the JDBC tracing
    assert(app.dataSource.isWrapperFor(HikariDataSource::class.java))
    // the set holds the very services the module provides
    assert(services.filterIsInstance<JdbcDataSourceService>().single() === app.jdbcDataSourceService)
    // readiness is down while the database doesn't answer
    assert(app.health.checks.single() is JdbcHealthCheck)
  }
}
