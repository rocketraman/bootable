package snippets.testing

import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule
import com.github.rocketraman.bootable.test.InMemoryTelemetry
import com.github.rocketraman.bootable.test.TestClock
import com.github.rocketraman.bootable.test.koin.inMemoryTelemetryModule
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.time.Clock

// tag::telemetry[]
fun openingASessionIsTracedAndCounted() {
  val koin = koinApplication {
    modules(
      bootableModule(),
      hopliteConfigModule(),
      openTelemetryModule(),
      inMemoryTelemetryModule(),  // its InMemoryTelemetry, one of openTelemetryModule()'s customizers
      sessionsModule,
      module { single<Clock> { TestClock() } },
    )
  }.koin
  val telemetry = koin.get<InMemoryTelemetry>()

  koin.get<Sessions>().open("ada")
  assert(telemetry.spans("open session").size == 1)
  assert(telemetry.metric("sessions.opened")!!.longSumData.points.single().value == 1L)
}
// end::telemetry[]
