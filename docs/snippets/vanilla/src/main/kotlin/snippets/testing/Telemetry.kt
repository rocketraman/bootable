package snippets.testing

import com.github.rocketraman.bootable.telemetry.otel.MetricsConfig
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.TelemetryConfig
import com.github.rocketraman.bootable.test.InMemoryTelemetry
import com.github.rocketraman.bootable.test.TestClock

// tag::telemetry[]
suspend fun openingASessionIsTracedAndCounted() {
  val telemetry = InMemoryTelemetry()
  val clock = TestClock()
  val otel = OpenTelemetryService(
    TelemetryConfig(registerGlobal = false, metrics = MetricsConfig(runtime = false)),
    listOf(telemetry),  // the in-memory telemetry is one of the SDK's customizers
    clock,
  )
  val sessions = Sessions(clock, otel.openTelemetry)

  sessions.open("ada")
  val span = telemetry.spans("open session").single()
  assert(span.startEpochNanos == clock.now().toEpochMilliseconds() * 1_000_000)
  assert(telemetry.metric("sessions.opened")!!.longSumData.points.single().value == 1L)
  otel.shutdown()
}
// end::telemetry[]
