package snippets.testing

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.test.InMemoryTelemetry
import com.github.rocketraman.bootable.test.InMemoryTelemetryBindings
import com.github.rocketraman.bootable.test.TestClock
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.time.Clock

// tag::telemetry[]
/** The application's bindings, with the in-memory telemetry and a clock the test passes in. */
@DependencyGraph(SessionsScope::class, additionalScopes = [BootScope::class])
interface SessionsTestGraph : BootableGraph {
  val sessions: Sessions

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Includes telemetry: InMemoryTelemetryBindings, @Provides clock: Clock): SessionsTestGraph
  }
}

fun openingASessionIsTracedAndCounted() {
  val telemetry = InMemoryTelemetry()
  val graph = createGraphFactory<SessionsTestGraph.Factory>().create(InMemoryTelemetryBindings(telemetry), TestClock())

  graph.sessions.open("ada")
  assert(telemetry.spans("open session").size == 1)
  assert(telemetry.metric("sessions.opened")!!.longSumData.points.single().value == 1L)
}
// end::telemetry[]
