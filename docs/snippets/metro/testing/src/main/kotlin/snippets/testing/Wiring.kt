package snippets.testing

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.test.checkShuffledStartPlans
import com.github.rocketraman.bootable.test.indexOfService
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import io.opentelemetry.api.OpenTelemetry
import kotlin.time.Clock

sealed interface SessionsScope

// tag::app[]
/** The application's own bindings, which the application's graph and the tests' share. */
@ContributesTo(SessionsScope::class) @BindingContainer
object SessionsBindings {
  @Provides @SingleIn(SessionsScope::class)
  fun sessions(clock: Clock, openTelemetry: OpenTelemetry): Sessions = Sessions(clock, openTelemetry)

  @Provides @IntoSet fun sessionReaper(sessions: Sessions): AppService = SessionReaper(sessions)
}

@DependencyGraph(SessionsScope::class, additionalScopes = [BootScope::class])
interface AppGraph : BootableGraph {
  @Provides fun clock(): Clock = Clock.System
}

fun main() = boot {
  createGraph<AppGraph>().bootable
}
// end::app[]

// tag::check[]
fun theWiringResolves() {
  // nothing is started: creating the graph is enough, and the compiler has checked it is complete
  val services = createGraph<AppGraph>().appServices
  assert(services.map { it::class }.toSet() == setOf(OpenTelemetryService::class, SessionReaper::class))

  checkShuffledStartPlans(services) { plan ->
    assert(plan.indexOfService<OpenTelemetryService>() < plan.indexOfService<SessionReaper>())
  }
}
// end::check[]
