package snippets.background

import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.clock.ClockModule
import com.github.rocketraman.bootable.clock.clockModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationModule
import com.github.rocketraman.bootable.db.flyway.flywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.HikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.SqliteConfigSource
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerModule
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerTaskProviders
import com.github.rocketraman.bootable.scheduler.db.dbSchedulerModule
import com.github.rocketraman.bootable.telemetry.otel.NoopOpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryApiModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

// tag::wire[]
@DIModule(composed = true)
interface SchedulerAppModule :
  ConfigModule, BootableModule, ClockModule, JdbcDataSourceModule, HikariPoolModule, FlywayMigrationModule,
  OpenTelemetryApiModule, DbSchedulerModule {
  val notifications: Notifications
  @DbSchedulerTaskProviders val reminders: Reminders  // its tasks, to the scheduler
  val reminderApi: ReminderApi
  // ... and the services that use ReminderApi
}

fun schedulerAppModule() = SchedulerAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  clockModule = clockModule(),
  jdbcDataSourceModule = jdbcDataSourceModule(SqliteConfigSource),
  hikariPoolModule = hikariPoolModule(),
  flywayMigrationModule = flywayMigrationModule(),   // migrates the scheduler's table
  openTelemetryApiModule = NoopOpenTelemetryModule,  // required: no tracing of the executions
  dbSchedulerModule = dbSchedulerModule(),           // the scheduler, from db.scheduler, and its schedulerClient
  notifications = Binding.single { Notifications() },
  reminders = Binding.single { Reminders(notifications) },
  reminderApi = Binding.single { ReminderApi(reminders, schedulerClient) },
)
// end::wire[]
