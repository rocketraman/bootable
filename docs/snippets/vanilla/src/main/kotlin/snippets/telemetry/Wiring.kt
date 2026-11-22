package snippets.telemetry

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.defaultLifecycleControllers
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.ServiceStartTracing
import com.github.rocketraman.bootable.telemetry.otel.telemetryConfig

fun main() = boot {
  val binder = hopliteConfigBinder()
  // tag::wire[]
  val telemetry = OpenTelemetryService(telemetryConfig(binder))  // from telemetry.sdk and the OTEL_* variables
  val index = NoteIndex(telemetry.openTelemetry)                 // hand its OpenTelemetry to what records telemetry

  Bootable(
    setOf(
      telemetry,
      // ... and the services that use `index`
    ),
    defaultLifecycleControllers() + ServiceStartTracing(telemetry.openTelemetry),  // the start, as one trace
  )
  // end::wire[]
}
