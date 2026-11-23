package snippets.background

import com.github.rocketraman.bootable.periodic.PeriodicTracingOptions
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerTracingOptions
import com.github.rocketraman.bootable.telemetry.otel.Sample
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule
import org.koin.dsl.module

val periodicTelemetryModule = module {
  // tag::periodic[]
  includes(openTelemetryModule())  // the application's OpenTelemetry, from boot-telemetry-otel: periodicModule() traces each run
  // end::periodic[]
}

val alwaysSampledPeriodicModule = module {
  // tag::periodic-options[]
  single { PeriodicTracingOptions(sample = Sample.Always) }  // or enabled = false, to trace no run
  // end::periodic-options[]
}

val schedulerTelemetryModule = module {
  // tag::scheduler[]
  includes(openTelemetryModule())  // dbSchedulerModule() traces each execution
  // end::scheduler[]
}

val alwaysSampledSchedulerModule = module {
  // tag::scheduler-options[]
  single { DbSchedulerTracingOptions(sample = Sample.Always) }  // or enabled = false, to trace no execution
  // end::scheduler-options[]
}
