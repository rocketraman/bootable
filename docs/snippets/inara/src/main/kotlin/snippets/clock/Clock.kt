package snippets.clock

import com.github.rocketraman.bootable.clock.ClockModule
import com.github.rocketraman.bootable.clock.clockModule
import com.github.rocketraman.bootable.test.TestClock
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import kotlin.time.Clock

// tag::wire[]
@DIModule
interface InvoicesModule : @DIModule.Required ClockModule {
  val invoices: Invoices
  val tokenSigner: TokenSigner
}

val invoicesModule = InvoicesModulePart(
  invoices = Binding.single { Invoices(clock) },
  tokenSigner = Binding.single { TokenSigner(javaClock) },
)

@DIModule(composed = true)
interface ClockAppModule : ClockModule, InvoicesModule

/** The application's module, on [clock]: `Clock.System`, or a test's. */
fun clockAppModule(clock: Clock = Clock.System) = ClockAppModule(
  clockModule = clockModule(clock),
  invoicesModule = invoicesModule,
)
// end::wire[]

// tag::test[]
fun invoicesAreDatedByTheClock() {
  val clock = TestClock()
  assert(clockAppModule(clock).invoices.issue().issuedAt == clock.now())
}
// end::test[]
