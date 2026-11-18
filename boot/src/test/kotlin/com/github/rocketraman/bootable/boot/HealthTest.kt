package com.github.rocketraman.bootable.boot

import de.infix.testBalloon.framework.core.testSuite
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Records [health]'s state, and whether it is live and ready, as it starts and stops. */
private class ObservingService(
  private val health: Health,
  private val events: MutableList<String>,
  private val failStart: Boolean = false,
) : AppService {
  override fun start() {
    events += "start: ${health.observe()}"
    check(!failStart) { "failed to start" }
  }

  override fun shutdown() {
    events += "stop: ${health.observe()}"
  }
}

private fun Health.observe() = "$state live=${liveness().healthy} ready=${readiness().healthy}"

/** Captures the application's normal stop, to stop it from the test. */
private class Stopper : LifecycleController {
  @Volatile lateinit var stop: (Int) -> Unit

  override fun appStart(serviceContext: ServiceContext, normalStop: (exitCode: Int) -> Unit) {
    stop = normalStop
  }
}

private fun awaitCondition(timeout: Duration = 10.seconds, condition: () -> Boolean) {
  val mark = TimeSource.Monotonic.markNow()
  while (!condition()) {
    check(mark.elapsedNow() < timeout) { "timed out" }
    Thread.sleep(5)
  }
}

/** Runs a [Bootable] of [services] and [health] on a thread of its own, until [block] has run, then stops it. */
private fun runApp(health: Health, services: Set<AppService>, block: () -> Unit = {}): Int {
  val stopper = Stopper()
  var exitCode: Int? = null
  val app = thread {
    exitCode = Bootable(services, setOf(AppStartStopLifecycleController(), stopper, health))
      .run(maxShutdownTime = 1_000_000)
  }
  awaitCondition { health.state == LifecycleState.Running }
  block()
  stopper.stop(0)
  app.join()
  return exitCode!!
}

val HealthTest by testSuite {
  test("is starting, then running, stopping, and stopped; live until stopped, and ready only while running") {
    val events = Collections.synchronizedList(mutableListOf<String>())
    val health = Health()
    assert(health.observe() == "Starting live=true ready=false")

    runApp(health, setOf(ObservingService(health, events))) {
      assert(health.observe() == "Running live=true ready=true")
    }

    assert(events == listOf("start: Starting live=true ready=false", "stop: Stopping live=true ready=false"))
    assert(health.observe() == "Stopped live=false ready=false")
  }

  test("a failed start goes from starting to stopping, without draining") {
    val events = mutableListOf<String>()
    val health = Health(config = ReadinessConfig(drainDelay = 1.hours))
    val bootable = Bootable(
      setOf(ObservingService(health, events), ObservingService(health, events, failStart = true)),
      setOf(AppStartStopLifecycleController(), health),
      tiebreaker = StartOrder.Tiebreaker.ByName,
    )

    assert(bootable.run(maxShutdownTime = 1_000_000) == -1)
    assert(events.first() == "start: Starting live=true ready=false")
    assert(events.last() == "stop: Stopping live=true ready=false")
    assert(health.state == LifecycleState.Stopped)
  }

  test("readiness is down for the drain delay before the services stop, while liveness stays up") {
    val events = Collections.synchronizedList(mutableListOf<String>())
    val drainDelay = 300.milliseconds
    val health = Health(config = ReadinessConfig(drainDelay = drainDelay))
    var stopped: TimeSource.Monotonic.ValueTimeMark? = null
    val service = object : AppService {
      override fun shutdown() {
        stopped = TimeSource.Monotonic.markNow()
      }
    }

    val stopper = Stopper()
    val app = thread {
      Bootable(setOf(service, ObservingService(health, events)), setOf(AppStartStopLifecycleController(), stopper, health))
        .run(maxShutdownTime = 1_000_000)
    }
    awaitCondition { health.state == LifecycleState.Running }
    stopper.stop(0)
    awaitCondition { health.state == LifecycleState.Stopping }
    val stopping = TimeSource.Monotonic.markNow()
    // draining: nothing has stopped yet
    assert(health.observe() == "Stopping live=true ready=false")
    assert(events.none { it.startsWith("stop") })
    app.join()

    assert(stopped!! - stopping >= drainDelay - 50.milliseconds)
    assert(events.last() == "stop: Stopping live=true ready=false")
  }

  test("readiness runs the checks while running, and is down while any is unhealthy or throws") {
    var dbUp = true
    val health = Health(
      setOf(
        HealthCheck("db") { if (dbUp) HealthStatus.Healthy else HealthStatus.Unhealthy("db is down") },
        HealthCheck("cache") { HealthStatus.Healthy },
      )
    )
    // not run until the application is running
    assert(health.readiness() == HealthReport(false, LifecycleState.Starting))

    runApp(health, emptySet()) {
      assert(health.readiness() == HealthReport(true, LifecycleState.Running, mapOf("db" to HealthStatus.Healthy, "cache" to HealthStatus.Healthy)))

      dbUp = false
      val ready = health.readiness()
      assert(!ready.healthy)
      assert(ready.checks["db"] == HealthStatus.Unhealthy("db is down"))
      // liveness doesn't depend on the checks
      assert(health.liveness() == HealthReport(true, LifecycleState.Running))
    }

    val throwing = Health(setOf(HealthCheck("boom") { error("kaboom") }))
    runApp(throwing, emptySet()) {
      assert(throwing.readiness().checks == mapOf("boom" to HealthStatus.Unhealthy("kaboom")))
    }
  }
}
