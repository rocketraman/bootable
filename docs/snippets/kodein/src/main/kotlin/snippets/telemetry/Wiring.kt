package snippets.telemetry

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.serviceStartTracingModule
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance

// tag::wire[]
fun appDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(openTelemetryModule)        // the SDK, from telemetry.sdk, as an OpenTelemetry binding
  import(serviceStartTracingModule)  // the start, as one trace
  // ... and the services that record telemetry, given instance<OpenTelemetry>()
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
// end::wire[]
