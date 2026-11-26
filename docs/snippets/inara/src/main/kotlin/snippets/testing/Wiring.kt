package snippets.testing

import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.setOfAppServices
import com.github.rocketraman.bootable.clock.ClockModule
import com.github.rocketraman.bootable.clock.clockModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryApiModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.test.checkShuffledStartPlans
import com.github.rocketraman.bootable.test.indexOfService
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

// tag::app[]
/** The application's own module, which `main` and the tests share. */
@DIModule
interface SessionsModule : @DIModule.Required OpenTelemetryApiModule, @DIModule.Required ClockModule {
  val sessions: Sessions
  @AppServices val sessionReaper: SessionReaper
}

/** The clock is the application's, from the `ClockModule`, which a test replaces. */
fun sessionsModule() = SessionsModulePart(
  sessions = Binding.single { Sessions(clock, openTelemetry) },
  sessionReaper = Binding.single { SessionReaper(sessions) },
)

@DIModule(composed = true)
interface AppModule : ConfigModule, BootableModule, ClockModule, OpenTelemetryModule, SessionsModule

fun appModule() = AppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  clockModule = clockModule(),
  openTelemetryModule = openTelemetryModule(),
  sessionsModule = sessionsModule(),
)

fun main() = boot {
  appModule().bootable
}
// end::app[]

// tag::check[]
fun theWiringResolves() {
  // nothing is started: building the module is enough, and the compiler has checked it is complete
  val services = appModule().setOfAppServices()
  assert(services.map { it::class }.toSet() == setOf(OpenTelemetryService::class, SessionReaper::class))

  checkShuffledStartPlans(services) { plan ->
    assert(plan.indexOfService<OpenTelemetryService>() < plan.indexOfService<SessionReaper>())
  }
}
// end::check[]
