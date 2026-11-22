package snippets.background

import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.db.jdbc.HikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.SqliteConfigSource
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.leader.AlwaysLeaderModule
import com.github.rocketraman.bootable.leader.LeaderElectionModule
import com.github.rocketraman.bootable.periodic.PeriodicModule
import com.github.rocketraman.bootable.periodic.PeriodicTracingOptions
import com.github.rocketraman.bootable.periodic.periodicModule
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerModule
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerTracingOptions
import com.github.rocketraman.bootable.scheduler.db.dbSchedulerModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.Sample
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import org.kodein.di.inara.DIModule

// tag::periodic[]
@DIModule(composed = true)
interface TracedPeriodicAppModule :
  // OpenTelemetryModule in place of OpenTelemetryApiModule: it is one, with the SDK
  ConfigModule, BootableModule, LeaderElectionModule, OpenTelemetryModule, PeriodicModule

fun tracedPeriodicAppModule() = TracedPeriodicAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  leaderElectionModule = AlwaysLeaderModule,
  // the application's OpenTelemetry, from boot-telemetry-otel: periodicModule traces each run
  openTelemetryModule = openTelemetryModule(),
  periodicModule = periodicModule(),
)
// end::periodic[]

fun alwaysSampledPeriodicAppModule() = TracedPeriodicAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  leaderElectionModule = AlwaysLeaderModule,
  openTelemetryModule = openTelemetryModule(),
  // tag::periodic-options[]
  // a parameter of the builder; or enabled = false, to trace no run
  periodicModule = periodicModule(tracingOptions = PeriodicTracingOptions(sample = Sample.Always)),
  // end::periodic-options[]
)

// tag::scheduler[]
@DIModule(composed = true)
interface TracedSchedulerAppModule :
  // OpenTelemetryModule in place of OpenTelemetryApiModule: it is one, with the SDK
  ConfigModule, BootableModule, JdbcDataSourceModule, HikariPoolModule, OpenTelemetryModule, DbSchedulerModule

fun tracedSchedulerAppModule() = TracedSchedulerAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  jdbcDataSourceModule = jdbcDataSourceModule(SqliteConfigSource),
  hikariPoolModule = hikariPoolModule(),
  openTelemetryModule = openTelemetryModule(),  // dbSchedulerModule traces each execution
  dbSchedulerModule = dbSchedulerModule(),
)
// end::scheduler[]

fun alwaysSampledSchedulerAppModule() = TracedSchedulerAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  jdbcDataSourceModule = jdbcDataSourceModule(SqliteConfigSource),
  hikariPoolModule = hikariPoolModule(),
  openTelemetryModule = openTelemetryModule(),
  // tag::scheduler-options[]
  // a parameter of the builder; or enabled = false, to trace no execution
  dbSchedulerModule = dbSchedulerModule(tracingOptions = DbSchedulerTracingOptions(sample = Sample.Always)),
  // end::scheduler-options[]
)
