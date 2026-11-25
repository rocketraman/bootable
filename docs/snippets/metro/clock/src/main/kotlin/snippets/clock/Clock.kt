package snippets.clock

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.test.TestClock
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createDynamicGraph
import java.time.Clock as JClock
import kotlin.time.Clock

sealed interface InvoicesScope

// tag::wire[]
@DependencyGraph(InvoicesScope::class, additionalScopes = [BootScope::class], bindingContainers = [InvoicesBindings::class])
interface InvoicesGraph {
  val invoices: Invoices
}

@BindingContainer
object InvoicesBindings {
  // boot-clock-metro provides both clocks
  @Provides fun invoices(clock: Clock): Invoices = Invoices(clock)
  @Provides fun tokenSigner(clock: JClock): TokenSigner = TokenSigner(clock)
}
// end::wire[]

// tag::test[]
/** A test's clock, in place of the graph's. */
@BindingContainer
class TestClockBindings(private val clock: TestClock) {
  @Provides fun clock(): Clock = clock
}

fun invoicesAreDatedByTheClock() {
  val clock = TestClock()
  val graph = createDynamicGraph<InvoicesGraph>(TestClockBindings(clock))
  assert(graph.invoices.issue().issuedAt == clock.now())
}
// end::test[]
