package snippets.database

import com.github.rocketraman.bootable.db.jdbc.hikariPoolMetricsModule
import com.github.rocketraman.bootable.db.jdbc.jdbcTracingModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import org.kodein.di.DI

val databaseTracingModule by DI.Module {
  // tag::tracing[]
  import(openTelemetryModule)      // the application's OpenTelemetry, from boot-telemetry-otel
  import(jdbcTracingModule())      // a span for each statement
  import(hikariPoolMetricsModule)  // the pool's metrics
  // end::tracing[]
}
