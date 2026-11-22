package snippets.testing

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.test.checkShuffledStartPlans
import com.github.rocketraman.bootable.test.indexOfService
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton
import kotlin.time.Clock

// tag::app[]
/** The application's own bindings, which `main` and the tests share. */
val sessionsModule by DI.Module {
  bindSingleton { Sessions(instance(), instance()) }
  inBindSet<AppService> { add { singleton { SessionReaper(instance()) } } }
}

fun appDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(openTelemetryModule)
  import(sessionsModule)
  bindSingleton<Clock> { Clock.System }
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
// end::app[]

// tag::check[]
fun theWiringResolves() {
  // nothing is started: resolving the services is enough
  val services = appDI().direct.instance<Set<AppService>>()
  assert(services.map { it::class }.toSet() == setOf(OpenTelemetryService::class, SessionReaper::class))

  checkShuffledStartPlans(services) { plan ->
    assert(plan.indexOfService<OpenTelemetryService>() < plan.indexOfService<SessionReaper>())
  }
}
// end::check[]
