package snippets.background

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.leader.LeaderElection
import com.github.rocketraman.bootable.periodic.PeriodicGate
import com.github.rocketraman.bootable.periodic.PeriodicServices
import com.github.rocketraman.bootable.periodic.periodicTracing
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerService
import com.github.rocketraman.bootable.scheduler.db.dbSchedulerTracing
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.Sample
import javax.sql.DataSource

fun tracedPeriodic(telemetry: OpenTelemetryService, leaderElection: LeaderElection) =
  // tag::periodic[]
  PeriodicServices(
    PeriodicGate.leader(leaderElection),
    interceptors = listOf(periodicTracing(telemetry.openTelemetry)),  // a trace for each run
  )
  // end::periodic[]

fun alwaysSampledPeriodic(telemetry: OpenTelemetryService) =
  // tag::periodic-options[]
  PeriodicServices(interceptors = listOf(periodicTracing(telemetry.openTelemetry, Sample.Always)))
  // end::periodic-options[]

fun tracedScheduler(binder: ConfigBinder, dataSource: DataSource, reminders: Reminders, telemetry: OpenTelemetryService) =
  // tag::scheduler[]
  DbSchedulerService(
    dataSource,
    binder.config("db.scheduler"),
    reminders.tasks(),
    customizer = dbSchedulerTracing(telemetry.openTelemetry),  // a trace for each execution
  )
  // end::scheduler[]

fun alwaysSampledScheduler(binder: ConfigBinder, dataSource: DataSource, reminders: Reminders, telemetry: OpenTelemetryService) =
  // tag::scheduler-options[]
  DbSchedulerService(
    dataSource,
    binder.config("db.scheduler"),
    reminders.tasks(),
    customizer = dbSchedulerTracing(telemetry.openTelemetry, Sample.Always),
  )
  // end::scheduler-options[]
