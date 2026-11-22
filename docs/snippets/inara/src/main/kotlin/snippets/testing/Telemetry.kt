package snippets.testing

import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.test.InMemoryTelemetryModule
import com.github.rocketraman.bootable.test.TestClock
import com.github.rocketraman.bootable.test.inMemoryTelemetryModule
import org.kodein.di.inara.DIModule

// tag::telemetry[]
@DIModule(composed = true)
interface SessionsTestModule :
  ConfigModule, BootableModule, OpenTelemetryModule, InMemoryTelemetryModule, SessionsModule

fun openingASessionIsTracedAndCounted() {
  val module = SessionsTestModule(
    configModule = hopliteConfigModule(),
    bootableModule = bootableModule(),
    openTelemetryModule = openTelemetryModule(),
    inMemoryTelemetryModule = inMemoryTelemetryModule(),  // one of the SDK's customizers
    sessionsModule = sessionsModule(clock = TestClock()),
  )
  val telemetry = module.inMemoryTelemetry

  module.sessions.open("ada")
  assert(telemetry.spans("open session").size == 1)
  assert(telemetry.metric("sessions.opened")!!.longSumData.points.single().value == 1L)
}
// end::telemetry[]
