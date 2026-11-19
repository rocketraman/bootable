package com.github.rocketraman.bootable.boot

import de.infix.testBalloon.framework.core.testSuite
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Records [health]'s state, and whether it is live and ready, as it starts and stops. */
private class ObservingService(
  private val health: Health,
  private val events: MutableList<String>,
  private val failStart: Boolean = false,
) : AppService {
  override suspend fun start() {
    events += "start: ${health.observe()}"
    check(!failStart) { "failed to start" }
  }

  override suspend fun shutdown() {
    events += "stop: ${health.observe()}"
  }
}

private suspend fun Health.observe() = "$state live=${liveness().healthy} ready=${readiness().healthy}"

/** Runs a [Bootable] of [services] and [health] until [block] has run, then stops it, in real time. */
private fun runApp(health: Health, services: Set<AppService>, block: suspend CoroutineScope.() -> Unit = {}): Int =
  runBlocking {
    val bootable = Bootable(services, setOf(AppStartStopLifecycleController(), health))
    val run = async { bootable.run() }
    health.states.first { it == LifecycleState.Running }
    block()
    bootable.stop()
    run.await()
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
    runTest {
      val events = mutableListOf<String>()
      val health = Health(config = ReadinessConfig(drainDelay = 1.hours))
      val bootable = Bootable(
        setOf(ObservingService(health, events), ObservingService(health, events, failStart = true)),
        setOf(AppStartStopLifecycleController(), health),
        tiebreaker = StartOrder.Tiebreaker.ByName,
      )

      assert(bootable.run() == -1)
      assert(events.first() == "start: Starting live=true ready=false")
      assert(events.last() == "stop: Stopping live=true ready=false")
      assert(health.state == LifecycleState.Stopped)
      assert(testScheduler.currentTime == 0L)
    }
  }

  test("readiness is down for the drain delay before the services stop, while liveness stays up") {
    runTest {
      val events = Collections.synchronizedList(mutableListOf<String>())
      val health = Health(config = ReadinessConfig(drainDelay = 5.seconds))
      var stopped = false
      val service = object : AppService {
        override suspend fun shutdown() {
          stopped = true
        }
      }
      val bootable = Bootable(setOf(service, ObservingService(health, events)), setOf(AppStartStopLifecycleController(), health))

      val run = async { bootable.run() }
      runCurrent()
      assert(health.state == LifecycleState.Running)
      bootable.stop()
      advanceTimeBy(5.seconds - 1.milliseconds)
      // draining: nothing has stopped yet
      assert(health.observe() == "Stopping live=true ready=false")
      assert(!stopped && events.none { it.startsWith("stop") })
      advanceTimeBy(2.milliseconds)
      assert(stopped)
      assert(run.await() == 0)
      assert(events.last() == "stop: Stopping live=true ready=false")
    }
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
    assert(runBlocking { health.readiness() } == HealthReadyReport(false, LifecycleState.Starting))

    runApp(health, emptySet()) {
      assert(health.readiness() == HealthReadyReport(true, LifecycleState.Running, mapOf("db" to HealthStatus.Healthy, "cache" to HealthStatus.Healthy)))

      dbUp = false
      val ready = health.readiness()
      assert(!ready.healthy)
      assert(ready.checks["db"] == HealthStatus.Unhealthy("db is down"))
      // liveness doesn't depend on the checks
      assert(health.liveness() == HealthLiveReport(true, LifecycleState.Running))
    }

    val throwing = Health(setOf(HealthCheck("boom") { error("kaboom") }))
    runApp(throwing, emptySet()) {
      assert(throwing.readiness().checks == mapOf("boom" to HealthStatus.Unhealthy("kaboom")))
    }
  }

  test("a check that doesn't answer within the check timeout is unhealthy, and isn't run again while it runs") {
    val release = CountDownLatch(1)
    val runs = AtomicInteger()
    val health = Health(
      setOf(
        // blocks, as a JDBC check would, ignoring cancellation
        HealthCheck("blocked") { runs.incrementAndGet(); release.await(); HealthStatus.Healthy },
        HealthCheck("suspended") { awaitCancellation() },
        HealthCheck("fine") { HealthStatus.Healthy },
      ),
      ReadinessConfig(checkTimeout = 100.milliseconds),
    )

    runApp(health, emptySet()) {
      val mark = TimeSource.Monotonic.markNow()
      val ready = health.readiness()
      // the checks run at once, so readiness takes one timeout, not one per check
      assert(mark.elapsedNow() < 2.seconds)
      assert(
        ready.checks == mapOf(
          "blocked" to HealthStatus.Unhealthy("no answer within 100ms"),
          "suspended" to HealthStatus.Unhealthy("no answer within 100ms"),
          "fine" to HealthStatus.Healthy,
        )
      )
      assert(!ready.healthy)
      health.readiness()
      assert(runs.get() == 1)

      release.countDown()
      while (runs.get() == 1 && health.readiness().checks["blocked"] != HealthStatus.Healthy) Unit
      assert(health.readiness().checks["blocked"] == HealthStatus.Healthy)
    }
  }

  test("the reports have each service's state") {
    val health = Health()
    val service = object : AppService {
      override fun name() = "pool"
    }
    runApp(health, setOf(service)) {
      assert(health.liveness().services == mapOf("pool" to ServiceState.Running))
      assert(health.readiness().services == mapOf("pool" to ServiceState.Running))
    }
    assert(health.liveness().services == mapOf("pool" to ServiceState.Stopped))
  }
}
