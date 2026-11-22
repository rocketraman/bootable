package snippets.testing

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.telemetryConfig
import com.github.rocketraman.bootable.test.checkShuffledStartPlans
import com.github.rocketraman.bootable.test.indexOfService
import kotlin.time.Clock

// tag::app[]
/** The application's wiring, which `main` and the tests share. */
class SessionsApp(binder: ConfigBinder, clock: Clock = Clock.System) {
  val telemetry = OpenTelemetryService(telemetryConfig(binder), clock = clock)
  val sessions = Sessions(clock, telemetry.openTelemetry)
  val services: Set<AppService> = setOf(telemetry, SessionReaper(sessions))
}

fun main() = boot {
  Bootable(SessionsApp(hopliteConfigBinder()).services)
}
// end::app[]

// tag::check[]
fun theWiringResolves() {
  // nothing is started: constructing the services is enough
  val services = SessionsApp(hopliteConfigBinder(report = false)).services
  assert(services.map { it::class }.toSet() == setOf(OpenTelemetryService::class, SessionReaper::class))

  checkShuffledStartPlans(services) { plan ->
    assert(plan.indexOfService<OpenTelemetryService>() < plan.indexOfService<SessionReaper>())
  }
}
// end::check[]
