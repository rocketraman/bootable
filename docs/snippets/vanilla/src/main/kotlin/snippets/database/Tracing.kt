package snippets.database

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.jdbc.HikariConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import com.github.rocketraman.bootable.db.jdbc.hikariPoolMetrics
import com.github.rocketraman.bootable.db.jdbc.jdbcTracing
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService

fun tracedDatabase(binder: ConfigBinder, telemetry: OpenTelemetryService): JdbcDataSourceService =
  // tag::tracing[]
  JdbcDataSourceService(
    binder.config<SqliteConfig>("db.jdbc"),
    HikariConnectionPoolFactory(
      binder.config("db.pool"),
      hikariPoolMetrics(telemetry.openTelemetry),  // the pool's metrics
    ),
    jdbcTracing(telemetry.openTelemetry),          // a span for each statement
  )
  // end::tracing[]
