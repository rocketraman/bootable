package snippets.database

import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.clock.ClockModule
import com.github.rocketraman.bootable.clock.clockModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.db.jdbc.HikariPoolMetricsModule
import com.github.rocketraman.bootable.db.jdbc.HikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.JdbcTracingModule
import com.github.rocketraman.bootable.db.jdbc.SqliteConfigSource
import com.github.rocketraman.bootable.db.jdbc.hikariPoolMetricsModule
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.jdbcTracingModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import org.kodein.di.inara.DIModule

// tag::tracing[]
@DIModule(composed = true)
interface TracedAppModule :
  ConfigModule, BootableModule, ClockModule, JdbcDataSourceModule, HikariPoolModule,
  OpenTelemetryModule, JdbcTracingModule, HikariPoolMetricsModule

fun tracedAppModule() = TracedAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  clockModule = clockModule(),
  jdbcDataSourceModule = jdbcDataSourceModule(SqliteConfigSource),
  hikariPoolModule = hikariPoolModule(),
  openTelemetryModule = openTelemetryModule(),          // the application's OpenTelemetry, from boot-telemetry-otel
  jdbcTracingModule = jdbcTracingModule(),              // a span for each statement
  hikariPoolMetricsModule = hikariPoolMetricsModule(),  // the pool's metrics
)
// end::tracing[]
