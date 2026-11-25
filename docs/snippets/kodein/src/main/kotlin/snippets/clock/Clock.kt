@file:Suppress("DEPRECATION") // the Kodein flavor is deprecated

package snippets.clock

import com.github.rocketraman.bootable.clock.clockModule
import com.github.rocketraman.bootable.test.TestClock
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.instance
import kotlin.time.Clock

// tag::wire[]
val invoicesModule by DI.Module {
  bindSingleton { Invoices(instance()) }
  bindSingleton { TokenSigner(instance()) }
}

/** The application's DI, on [clock]: `Clock.System`, or a test's. */
fun appDI(clock: Clock = Clock.System) = DI {
  import(clockModule(clock))
  import(invoicesModule)
}
// end::wire[]

// tag::test[]
fun invoicesAreDatedByTheClock() {
  val clock = TestClock()
  val invoices = appDI(clock).direct.instance<Invoices>()
  assert(invoices.issue().issuedAt == clock.now())
}
// end::test[]
