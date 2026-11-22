package snippets.testing

import com.github.rocketraman.bootable.test.LogCapture
import com.github.rocketraman.bootable.test.TestClock
import com.github.rocketraman.bootable.test.captureLogs
import io.opentelemetry.api.OpenTelemetry
import org.apache.logging.log4j.Level
import kotlin.time.Duration.Companion.minutes

// tag::component-test[]
fun sessionsAreValid() {
  // constructed as the wiring constructs it, with a fake for each thing it uses
  val sessions = Sessions(TestClock(), OpenTelemetry.noop())

  val session = sessions.open("ada")
  assert(session.user == "ada")
  assert(sessions.isValid(session))
}
// end::component-test[]

// tag::clock[]
fun sessionsExpire() {
  val clock = TestClock()  // stands still at TestClock.DEFAULT_START
  val sessions = Sessions(clock, OpenTelemetry.noop(), ttl = 30.minutes)
  val session = sessions.open("ada")

  clock.advanceBy(29.minutes)
  assert(sessions.isValid(session))
  clock.advanceBy(1.minutes)
  assert(!sessions.isValid(session))
}
// end::clock[]

// tag::logs[]
fun openingASessionIsLogged() {
  val sessions = Sessions(TestClock(), OpenTelemetry.noop())

  val logs = captureLogs("snippets.testing") {  // the logger, and its descendants
    sessions.open("ada")
  }
  assert(logs.messages(Level.INFO) == listOf("Opened session ada-0"))
  // the one matching event, or an AssertionError that lists what was captured
  val event = logs.single(Level.INFO) { "ada" in it.message.formattedMessage }
  assert(event.loggerName == Sessions::class.qualifiedName)
}
// end::logs[]

// tag::log-capture[]
fun reapingIsQuiet() {
  val sessions = Sessions(TestClock(), OpenTelemetry.noop())
  LogCapture("snippets.testing", level = Level.DEBUG).use { logs ->  // DEBUG too, while it captures
    sessions.reap()
    assert(logs.events.isEmpty()) { logs.describe() }
  }
}
// end::log-capture[]
