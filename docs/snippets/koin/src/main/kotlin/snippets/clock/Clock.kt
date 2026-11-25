package snippets.clock

import com.github.rocketraman.bootable.clock.koin.clockModule
import com.github.rocketraman.bootable.test.TestClock
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.time.Clock

// tag::wire[]
val invoicesModule = module {
  single { Invoices(get()) }
  single { TokenSigner(get()) }
}

/** The application's modules, on [clock]: `Clock.System`, or a test's. */
fun appModules(clock: Clock = Clock.System) = listOf(clockModule(clock), invoicesModule)
// end::wire[]

// tag::test[]
fun invoicesAreDatedByTheClock() {
  val clock = TestClock()
  val koin = koinApplication { modules(appModules(clock)) }.koin
  try {
    assert(koin.get<Invoices>().issue().issuedAt == clock.now())
  } finally {
    koin.close()
  }
}
// end::test[]
