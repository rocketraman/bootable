package snippets.clock

import com.github.rocketraman.bootable.clock.asJavaClock
import com.github.rocketraman.bootable.test.TestClock
import kotlin.time.Clock

// tag::wire[]
/** The application's components, on [clock]: `Clock.System`, or a test's. */
class Components(clock: Clock = Clock.System) {
  val invoices = Invoices(clock)
  val tokenSigner = TokenSigner(clock.asJavaClock())
}
// end::wire[]

// tag::test[]
fun invoicesAreDatedByTheClock() {
  val clock = TestClock()
  val components = Components(clock)
  assert(components.invoices.issue().issuedAt == clock.now())
}
// end::test[]
