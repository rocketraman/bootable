package snippets.clock

import java.time.Clock as JClock
import java.time.Instant as JInstant
import kotlin.time.Clock
import kotlin.time.Instant

data class Invoice(val issuedAt: Instant)

/** The application's own class, which dates each invoice it issues by its `kotlin.time.Clock`. */
class Invoices(private val clock: Clock) {
  fun issue(): Invoice = Invoice(issuedAt = clock.now())
}

/** A class, such as a library's, which takes a `java.time.Clock`. */
class TokenSigner(private val clock: JClock) {
  fun expiry(): JInstant = clock.instant().plusSeconds(3600)
}
