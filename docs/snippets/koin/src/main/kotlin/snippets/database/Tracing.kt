package snippets.database

import com.github.rocketraman.bootable.db.jdbc.koin.hikariPoolMetricsModule
import com.github.rocketraman.bootable.db.jdbc.koin.jdbcTracingModule
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule
import org.koin.dsl.module

val databaseTracingModule = module {
  // tag::tracing[]
  includes(openTelemetryModule())      // the application's OpenTelemetry, from boot-telemetry-otel
  includes(jdbcTracingModule())      // a span for each statement
  includes(hikariPoolMetricsModule())  // the pool's metrics
  // end::tracing[]
}
