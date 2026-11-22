package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.startPlan
import com.github.rocketraman.bootable.leader.LeaderElection
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

val PeriodicAppServiceTest by testSuite {
  test("runs its work every interval") {
    val runs = AtomicInteger()
    val service = PeriodicAppService("counter", testConfig()) { runs.incrementAndGet() }
    service.startService()
    try {
      eventually { runs.get() >= 3 }
      assert(service.isRunning)
      assert(service.lastStarted != null && service.lastSucceeded != null)
    } finally {
      service.shutdown()
    }
    assert(!service.isRunning)
  }

  test("waits the interval between one run's end and the next one's start") {
    val starts = CopyOnWriteArrayList<TimeSource.Monotonic.ValueTimeMark>()
    val ends = CopyOnWriteArrayList<TimeSource.Monotonic.ValueTimeMark>()
    val service = PeriodicAppService("spaced", testConfig(interval = 50.milliseconds)) {
      starts += TimeSource.Monotonic.markNow()
      delay(30.milliseconds)
      ends += TimeSource.Monotonic.markNow()
    }
    service.startService()
    try {
      eventually { starts.size >= 3 }
    } finally {
      service.shutdown()
    }
    assert((1 until starts.size).all { starts[it] - ends[it - 1] >= 50.milliseconds })
  }

  test("skips its runs while its gate is closed") {
    val runs = AtomicInteger()
    val open = AtomicBoolean(false)
    val service = PeriodicAppService("gated", testConfig(), gate = { open.get() }) { runs.incrementAndGet() }
    service.startService()
    try {
      sleep(100.milliseconds)
      assert(runs.get() == 0)
      assert(service.lastStarted == null)
      open.set(true)
      eventually { runs.get() >= 2 }
    } finally {
      service.shutdown()
    }
  }

  test("runs only while this instance leads") {
    val leading = MutableStateFlow(false)
    val election = object : LeaderElection {
      override val leadership = leading
      override val fencingToken get() = if (leading.value) 1L else null
    }
    val runs = AtomicInteger()
    val service = PeriodicAppService("led", testConfig(), PeriodicGate.leader(election)) { runs.incrementAndGet() }
    service.startService()
    try {
      sleep(100.milliseconds)
      assert(runs.get() == 0)
      leading.value = true
      eventually { runs.get() >= 2 }
      leading.value = false
      // a run may have been under way when leadership was lost
      sleep(50.milliseconds)
      val stopped = runs.get()
      sleep(100.milliseconds)
      assert(runs.get() == stopped)
    } finally {
      service.shutdown()
    }
  }

  test("cancels a run in progress when this instance stops leading, without counting it as a failure") {
    val leading = MutableStateFlow(true)
    val election = object : LeaderElection {
      override val leadership = leading
      override val fencingToken get() = if (leading.value) 1L else null
    }
    val started = AtomicBoolean(false)
    val cancelled = AtomicBoolean(false)
    val service = PeriodicAppService("led", testConfig(), PeriodicGate.leader(election)) {
      started.set(true)
      try {
        delay(10.seconds)
      } catch (e: CancellationException) {
        cancelled.set(true)
        throw e
      }
    }
    service.startService()
    try {
      eventually { started.get() }
      leading.value = false
      eventually { cancelled.get() }
      sleep(50.milliseconds)
      assert(service.isRunning)
      assert(service.consecutiveFailures == 0)
      assert(service.lastFailed == null)
      assert(service.lastSucceeded == null)
      // and it runs again once this instance leads again
      started.set(false)
      leading.value = true
      eventually { started.get() }
    } finally {
      service.shutdown()
    }
  }

  test("combined gates cancel a run in progress when either closes") {
    val leading = MutableStateFlow(true)
    val election = object : LeaderElection {
      override val leadership = leading
      override val fencingToken get() = if (leading.value) 1L else null
    }
    val cancelled = AtomicBoolean(false)
    val started = AtomicBoolean(false)
    val gate = PeriodicGate.Always and PeriodicGate.leader(election)
    val service = PeriodicAppService("combined", testConfig(), gate) {
      started.set(true)
      try {
        delay(10.seconds)
      } catch (e: CancellationException) {
        cancelled.set(true)
        throw e
      }
    }
    service.startService()
    try {
      eventually { started.get() }
      leading.value = false
      eventually { cancelled.get() }
    } finally {
      service.shutdown()
    }
  }

  test("runs only while the application is running, with the running gate") {
    val health = HealthLifecycleController()
    val runs = AtomicInteger()
    val service = PeriodicAppService("running", testConfig(), PeriodicGate.running(health)) { runs.incrementAndGet() }
    service.startService()
    try {
      sleep(100.milliseconds)
      assert(runs.get() == 0)
      health.appRunning()
      eventually { runs.get() >= 2 }
    } finally {
      service.shutdown()
    }
  }

  test("a gate that throws skips the run") {
    val runs = AtomicInteger()
    val service = PeriodicAppService("broken-gate", testConfig(), gate = { error("no gate") }) { runs.incrementAndGet() }
    service.startService()
    try {
      sleep(100.milliseconds)
      assert(runs.get() == 0)
      assert(service.isRunning)
    } finally {
      service.shutdown()
    }
  }

  test("carries on after a run fails, handing the failure to its handler") {
    val runs = AtomicInteger()
    val failures = CopyOnWriteArrayList<Pair<Throwable, Int>>()
    val service = PeriodicAppService(
      "flaky",
      testConfig(),
      onFailure = { _, failure, consecutive -> failures += failure to consecutive },
    ) {
      // NotImplementedError, from a TODO(), is an Error, which the loop survives too
      when (runs.incrementAndGet()) {
        1 -> error("first")
        2 -> TODO("second")
      }
    }
    service.startService()
    try {
      eventually { runs.get() >= 3 }
      eventually { service.lastSucceeded != null }
    } finally {
      service.shutdown()
    }
    assert(failures.map { it.first.message } == listOf("first", "An operation is not implemented: second"))
    assert(failures.map { it.second } == listOf(1, 2))
    assert(service.consecutiveFailures == 0)
    assert(service.lastFailed != null)
  }

  test("a failure handler that throws doesn't stop the loop") {
    val runs = AtomicInteger()
    val service = PeriodicAppService("bad-handler", testConfig(), onFailure = { _, _, _ -> error("handler") }) {
      runs.incrementAndGet()
      error("run")
    }
    service.startService()
    try {
      eventually { runs.get() >= 3 }
      assert(service.isRunning)
    } finally {
      service.shutdown()
    }
  }

  test("stops the loop once the failure limit is reached") {
    val runs = AtomicInteger()
    val service = PeriodicAppService("poisoned", testConfig(maxConsecutiveFailures = 3)) {
      runs.incrementAndGet()
      error("poison pill")
    }
    service.startService()
    try {
      eventually { !service.isRunning }
      assert(runs.get() == 3)
      assert(service.consecutiveFailures == 3)
    } finally {
      service.shutdown()
    }
  }

  test("a success resets the consecutive failures") {
    val runs = AtomicInteger()
    val service = PeriodicAppService("intermittent", testConfig(maxConsecutiveFailures = 2)) {
      // fails every other run, so never twice in a row
      if (runs.incrementAndGet() % 2 == 1) error("odd run")
    }
    service.startService()
    try {
      eventually { runs.get() >= 6 }
      assert(service.isRunning)
    } finally {
      service.shutdown()
    }
  }

  test("shuts the application down at the failure limit, when configured to") {
    val died = AtomicBoolean()
    val config = testConfig(maxConsecutiveFailures = 2, onFailureLimit = FailureLimitAction.Shutdown)
    val service = PeriodicAppService("fatal", config) { error("poison pill") }
    service.startService { died.set(true) }
    try {
      eventually { died.get() }
    } finally {
      service.shutdown()
    }
  }

  test("a run that exceeds its timeout is cancelled, and fails") {
    val failures = CopyOnWriteArrayList<Throwable>()
    val service = PeriodicAppService(
      "slow",
      testConfig(runTimeout = 20.milliseconds),
      onFailure = { _, failure, _ -> failures += failure },
    ) { delay(10.seconds) }
    service.startService()
    try {
      eventually { failures.size >= 2 }
      assert(service.isRunning)
    } finally {
      service.shutdown()
    }
    assert(failures.all { it is CancellationException })
  }

  test("shutdown lets the run in progress finish, and starts no other") {
    val started = AtomicInteger()
    val finished = AtomicInteger()
    val service = PeriodicAppService("draining", testConfig(interval = 10.milliseconds)) {
      started.incrementAndGet()
      delay(200.milliseconds)
      finished.incrementAndGet()
    }
    service.startService()
    eventually { started.get() == 1 }
    service.shutdown()
    assert(finished.get() == 1)
    sleep(50.milliseconds)
    assert(started.get() == 1)
    assert(!service.isRunning)
  }

  test("shutdown doesn't wait for the interval") {
    val service = PeriodicAppService("sleepy", testConfig(interval = 1.seconds * 60)) {}
    service.startService()
    eventually { service.lastSucceeded != null }
    val mark = TimeSource.Monotonic.markNow()
    service.shutdown()
    assert(mark.elapsedNow() < 1.seconds)
  }

  test("shutdown cancels a run that outlasts the shutdown timeout") {
    val cancelled = AtomicBoolean()
    val service = PeriodicAppService("stuck", testConfig(shutdownTimeout = 50.milliseconds)) {
      try {
        delay(60.seconds)
      } catch (e: CancellationException) {
        cancelled.set(true)
        throw e
      }
    }
    service.startService()
    eventually { service.lastStarted != null }
    val mark = TimeSource.Monotonic.markNow()
    service.shutdown()
    assert(mark.elapsedNow() < 1.seconds)
    eventually { cancelled.get() }
  }

  test("a disabled loop starts nothing") {
    val runs = AtomicInteger()
    val service = PeriodicAppService("disabled", testConfig(enabled = false)) { runs.incrementAndGet() }
    service.startService()
    sleep(50.milliseconds)
    assert(runs.get() == 0)
    assert(!service.isRunning)
    service.shutdown()
  }

  test("the first run waits for the initial delay") {
    val runs = AtomicInteger()
    val config = testConfig().copy(initialDelay = 200.milliseconds)
    val service = PeriodicAppService("delayed", config) { runs.incrementAndGet() }
    service.startService()
    try {
      sleep(50.milliseconds)
      assert(runs.get() == 0)
      eventually { runs.get() >= 1 }
    } finally {
      service.shutdown()
    }
  }

  test("the interceptors run around each run, the first outermost") {
    val events = Collections.synchronizedList(mutableListOf<String>())
    fun interceptor(id: String) = object : PeriodicRunInterceptor {
      override suspend fun intercept(service: String, run: suspend () -> Unit) {
        events += "$id>$service"
        run()
        events += "$id<"
      }
    }
    val service = PeriodicAppService(
      "intercepted", testConfig(), interceptors = listOf(interceptor("a"), interceptor("b")),
    ) { events += "run" }
    service.startService()
    try {
      eventually { events.size >= 5 }
    } finally {
      service.shutdown()
    }
    assert(events.take(5) == listOf("a>intercepted", "b>intercepted", "run", "b<", "a<"))
  }

  test("times its runs by its clock") {
    val now = Instant.parse("2026-01-01T00:00:00Z")
    val clock = object : Clock {
      override fun now() = now
    }
    val service = PeriodicAppService("clocked", testConfig(), clock = clock) {}
    service.startService()
    try {
      eventually { service.lastSucceeded != null }
    } finally {
      service.shutdown()
    }
    assert(service.lastStarted == now && service.lastSucceeded == now)
  }

  test("starts in the Serving phase, after the application's services") {
    val loop = PeriodicAppService("loop", testConfig()) {}
    val app = object : AppService {}
    val infrastructure = object : AppService {
      override fun ordering() = Ordering(DefaultPhase.Infrastructure)
    }
    val plan = startPlan(setOf(loop, app, infrastructure), tiebreaker = StartOrder.Tiebreaker.Shuffled())
    assert(plan == listOf(infrastructure, app, loop))
    assert(loop.name() == "loop")
  }

  test("PeriodicServices shares its settings with the services it creates") {
    val events = CopyOnWriteArrayList<String>()
    val interceptor = object : PeriodicRunInterceptor {
      override suspend fun intercept(service: String, run: suspend () -> Unit) {
        events += "intercepted $service"
        run()
      }
    }
    val periodic = PeriodicServices(gate = { false }, interceptors = listOf(interceptor))
    val closed = periodic.create("closed", testConfig()) { events += "closed ran" }
    val open = periodic.create("open", testConfig(), gate = PeriodicGate.Always) { events += "open ran" }
    closed.startService()
    open.startService()
    try {
      eventually { "open ran" in events }
      sleep(50.milliseconds)
    } finally {
      closed.shutdown()
      open.shutdown()
    }
    assert("closed ran" !in events)
    assert("intercepted open" in events)
  }

  test("the config rejects settings that make no sense") {
    assert(runCatching { PeriodicConfig(interval = 0.seconds) }.isFailure)
    assert(runCatching { PeriodicConfig(maxConsecutiveFailures = 0) }.isFailure)
    assert(runCatching { PeriodicConfig(runTimeout = 0.seconds) }.isFailure)
  }

  test("gates combine") {
    assert((PeriodicGate.Always and PeriodicGate { false }).isOpen().not())
    assert((PeriodicGate.Always and PeriodicGate.Always).isOpen())
  }
}
