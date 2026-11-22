package snippets.background

import com.github.rocketraman.bootable.periodic.PeriodicTracingOptions
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerTracingOptions
import com.github.rocketraman.bootable.telemetry.otel.Sample
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import org.kodein.di.DI
import org.kodein.di.bindSingleton

val periodicTelemetryModule by DI.Module {
  // tag::periodic[]
  import(openTelemetryModule)  // the application's OpenTelemetry, from boot-telemetry-otel: periodicModule traces each run
  // end::periodic[]
}

val alwaysSampledPeriodicModule by DI.Module {
  // tag::periodic-options[]
  bindSingleton { PeriodicTracingOptions(sample = Sample.Always) }  // or enabled = false, to trace no run
  // end::periodic-options[]
}

val schedulerTelemetryModule by DI.Module {
  // tag::scheduler[]
  import(openTelemetryModule)  // dbSchedulerModule traces each execution
  // end::scheduler[]
}

val alwaysSampledSchedulerModule by DI.Module {
  // tag::scheduler-options[]
  bindSingleton { DbSchedulerTracingOptions(sample = Sample.Always) }  // or enabled = false, to trace no execution
  // end::scheduler-options[]
}
