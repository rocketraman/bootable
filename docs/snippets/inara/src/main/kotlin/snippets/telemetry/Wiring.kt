package snippets.telemetry

import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.ServiceStartTracingModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.serviceStartTracingModule
import org.kodein.di.inara.DIModule

// tag::wire[]
@DIModule(composed = true)
interface AppModule : ConfigModule, BootableModule, OpenTelemetryModule, ServiceStartTracingModule {
  // ... and the services that record telemetry, given openTelemetry
}

fun appModule() = AppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),              // the SDK, from telemetry.sdk, as the openTelemetry member
  serviceStartTracingModule = serviceStartTracingModule(),  // the start, as one trace
)

fun main() = boot {
  appModule().bootable
}
// end::wire[]
