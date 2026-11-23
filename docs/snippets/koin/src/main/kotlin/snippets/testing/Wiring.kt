package snippets.testing

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.koin.openTelemetryModule
import com.github.rocketraman.bootable.test.checkShuffledStartPlans
import com.github.rocketraman.bootable.test.indexOfService
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.time.Clock

// tag::app[]
/** The application's own definitions, which `main` and the tests share. */
val sessionsModule = module {
  single { Sessions(get(), get()) }
  single<AppService>(named<SessionReaper>()) { SessionReaper(get()) }
}

fun appKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    openTelemetryModule(),
    sessionsModule,
    module { single<Clock> { Clock.System } },
  )
}.koin

fun main() = boot {
  appKoin().get<Bootable>()
}
// end::app[]

// tag::check[]
fun theWiringResolves() {
  // nothing is started: resolving the services is enough
  val services = appKoin().getAll<AppService>().toSet()
  assert(services.map { it::class }.toSet() == setOf(OpenTelemetryService::class, SessionReaper::class))

  checkShuffledStartPlans(services) { plan ->
    assert(plan.indexOfService<OpenTelemetryService>() < plan.indexOfService<SessionReaper>())
  }
}
// end::check[]
