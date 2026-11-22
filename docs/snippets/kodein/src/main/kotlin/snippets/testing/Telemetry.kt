package snippets.testing

import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.test.InMemoryTelemetry
import com.github.rocketraman.bootable.test.TestClock
import com.github.rocketraman.bootable.test.inMemoryTelemetryModule
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.instance
import kotlin.time.Clock

// tag::telemetry[]
fun openingASessionIsTracedAndCounted() {
  val di = DI {
    import(bootableModule)
    import(hopliteConfigModule)
    import(openTelemetryModule)
    import(inMemoryTelemetryModule)  // after openTelemetryModule, which opens the customizers' set
    import(sessionsModule)
    bindSingleton<Clock> { TestClock() }
  }.direct
  val telemetry = di.instance<InMemoryTelemetry>()

  di.instance<Sessions>().open("ada")
  assert(telemetry.spans("open session").size == 1)
  assert(telemetry.metric("sessions.opened")!!.longSumData.points.single().value == 1L)
}
// end::telemetry[]
