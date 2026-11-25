package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.boot.DefaultPhase.Application
import com.github.rocketraman.bootable.boot.DefaultPhase.Infrastructure
import com.github.rocketraman.bootable.boot.DefaultPhase.Initialization
import com.github.rocketraman.bootable.boot.DefaultPhase.Serving
import de.infix.testBalloon.framework.core.testSuite
import java.util.Collections
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private class RecordingService(
  override val name: String,
  private val phase: Phase,
  private val events: MutableList<String>,
  private val failStart: Boolean = false,
  private val startTime: kotlin.time.Duration? = null,
  private val stopTime: kotlin.time.Duration? = null,
) : AppService {
  override fun ordering() = Ordering(phase)

  override suspend fun start() {
    events += "start $name"
    startTime?.let { delay(it) }
    check(!failStart) { "$name failed to start" }
  }

  override suspend fun shutdown() {
    events += "stop $name"
    stopTime?.let { delay(it) }
    events += "stopped $name"
  }
}

/**
 * A service with background work, in its scope: [job], which completing [failure] fails, and [sibling], which runs
 * until it is cancelled. Both run on [context], the test's
 * dispatcher, so that its work is in virtual time: a test that joined work on a real dispatcher would let virtual time
 * run on to the shutdown timeout meanwhile.
 */
private class BackgroundService(
  override val name: String,
  private val events: MutableList<String>,
  private val context: CoroutineContext,
  override val scopeFailurePolicy: ScopeFailurePolicy = ScopeFailurePolicy.StopApplication(),
) : AppService {
  val failure = CompletableDeferred<Unit>()
  var job: Job? = null
  var sibling: Job? = null

  override suspend fun start(scope: CoroutineScope) {
    events += "start $name"
    job = scope.launch(context) {
      failure.await()
      error("$name's background work failed")
    }
    sibling = scope.launch(context) { awaitCancellation() }
  }

  override suspend fun shutdown() {
    events += "stop $name"
  }
}

/** Votes against starting [name]. */
private class Veto(private val name: String) : LifecycleController {
  override fun serviceStartVote(appService: AppService) =
    if (appService.name == name) ServiceStartVote.NO else ServiceStartVote.YES
}

/**
 * Records its code around the application's start, as `name{` and `}name`, around each service's start, as `name(`
 * and `)name`, and its [appRunning], as `name!`.
 */
private class Around(
  private val name: String,
  private val priority: Int,
  private val events: MutableList<String>,
) : LifecycleController {
  override fun priority() = priority

  override suspend fun aroundAppStart(serviceContext: ServiceContext, timeline: StartupTimeline, start: suspend () -> Unit) {
    events += "$name{"
    try {
      start()
    } finally {
      events += "}$name"
    }
  }

  override suspend fun aroundServiceStart(appService: AppService, start: suspend () -> Unit) {
    events += "$name("
    try {
      start()
    } finally {
      events += ")$name"
    }
  }

  override suspend fun appRunning() {
    events += "$name!"
  }
}

/** Stops the application once it is running, as `stopping`, with the lowest priority, so after the others' [appRunning]. */
private class StopsWhenRunning(private val events: MutableList<String>) : LifecycleController {
  private lateinit var stop: (Int) -> Unit

  override suspend fun appStart(serviceContext: ServiceContext, normalStop: (exitCode: Int) -> Unit) {
    stop = normalStop
  }

  override suspend fun appRunning() {
    events += "stopping"
    stop(0)
  }

  override fun priority() = Int.MIN_VALUE
}

/** Stops the application as it starts, before Bootable waits for a stop, as a signal during the start would. */
private class StopsAtStart : LifecycleController {
  override suspend fun appStart(serviceContext: ServiceContext, normalStop: (exitCode: Int) -> Unit) = normalStop(5)
}

/** Captures the service context, to read the services' states. */
private class Observer : LifecycleController {
  lateinit var context: ServiceContext
  override suspend fun aroundAppStart(serviceContext: ServiceContext, timeline: StartupTimeline, start: suspend () -> Unit) {
    context = serviceContext
    start()
  }
  fun states() = context.states.value.entries.associate { (service, state) -> service.name to state }
}

private fun events() = Collections.synchronizedList(mutableListOf<String>())

val BootableTest by testSuite {
  test("a failed start stops the services that started, in reverse, but not the one that failed") {
    runTest {
      val events = events()
      val observer = Observer()
      val bootable = Bootable(
        setOf(
          RecordingService("pool", Infrastructure, events),
          RecordingService("skipped", Infrastructure, events),
          RecordingService("migrate", Initialization, events, failStart = true),
          RecordingService("app", Application, events),
          RecordingService("server", Serving, events),
        ),
        setOf(AppStartStopLifecycleController(), Veto("skipped"), observer),
      )

      assert(bootable.run() == -1)
      assert(events == listOf("start pool", "start migrate", "stop pool", "stopped pool"))
      assert(
        observer.states() == mapOf(
          "pool" to ServiceState.Stopped,
          "skipped" to ServiceState.Skipped,
          "migrate" to ServiceState.Failed,
          "app" to ServiceState.Pending,
          "server" to ServiceState.Pending,
        )
      )
    }
  }

  test("controllers run around the start and each service's, the highest priority outermost, and see its failure") {
    runTest {
      val events = events()
      val bootable = Bootable(
        setOf(
          RecordingService("pool", Infrastructure, events),
          RecordingService("skipped", Infrastructure, events),
          RecordingService("migrate", Initialization, events, failStart = true),
        ),
        setOf(AppStartStopLifecycleController(), Veto("skipped"), Around("inner", 1, events), Around("outer", 2, events)),
      )

      assert(bootable.run() == -1)
      assert(
        events == listOf(
          "outer{", "inner{",
          "outer(", "inner(", "start pool", ")inner", ")outer",
          "outer(", "inner(", "start migrate", ")inner", ")outer",
          "}inner", "}outer",
          "stop pool", "stopped pool",
        )
      )
    }
  }

  test("once the whole start has completed, the controllers are told the application is running, highest first") {
    runTest {
      val events = events()
      val bootable = Bootable(
        setOf(RecordingService("pool", Infrastructure, events)),
        setOf(AppStartStopLifecycleController(), Around("inner", 1, events), Around("outer", 2, events), StopsWhenRunning(events)),
      )

      assert(bootable.run() == 0)
      assert(
        events == listOf(
          "outer{", "inner{", "outer(", "inner(", "start pool", ")inner", ")outer", "}inner", "}outer",
          "outer!", "inner!", "stopping",
          "stop pool", "stopped pool",
        )
      )
    }
  }

  test("a controller that fails when the application is running fails the start, and the controllers after it aren't told") {
    runTest {
      val events = events()
      val failing = object : LifecycleController {
        override suspend fun appRunning() = error("not running after all")
        override fun priority() = 2
      }
      val bootable = Bootable(
        setOf(RecordingService("pool", Infrastructure, events)),
        setOf(AppStartStopLifecycleController(), failing, Around("lower", 1, events)),
      )

      assert(bootable.run() == -1)
      assert(events == listOf("lower{", "lower(", "start pool", ")lower", "}lower", "stop pool", "stopped pool"))
    }
  }

  test("the controllers get the timeline of the boot, with the planning and initialization added to its stages") {
    runTest {
      var captured: StartupTimeline? = null
      val capture = object : LifecycleController {
        override suspend fun aroundAppStart(serviceContext: ServiceContext, timeline: StartupTimeline, start: suspend () -> Unit) {
          captured = timeline
          start()
        }
      }
      val started = TimeSource.Monotonic.markNow() - 1.seconds
      val booted = StartupTimeline(started).then(StartupTimeline.INITIALIZE_JVM_CLASSES, started + 0.5.seconds)
      val bootable = Bootable(
        setOf(RecordingService("pool", Infrastructure, mutableListOf(), failStart = true)),
        setOf(AppStartStopLifecycleController(), capture),
      )

      assert(bootable.run(booted, 30.seconds) == -1)
      val timeline = captured!!
      val stages = timeline.stages
      assert(timeline.start == started)
      assert(
        stages.map { it.name } ==
          listOf(StartupTimeline.INITIALIZE_JVM_CLASSES, StartupTimeline.PLAN_START, StartupTimeline.INITIALIZE),
      )
      // each stage begins where the one before it ended
      assert(stages.zipWithNext().all { (before, after) -> before.end == after.start })
      assert(stages.first().start == started)
    }
  }

  test("runs until stopped, then stops the services in reverse, with the stop's exit code") {
    runTest {
      val events = events()
      val observer = Observer()
      val bootable = Bootable(
        setOf(RecordingService("pool", Infrastructure, events), RecordingService("server", Serving, events)),
        setOf(AppStartStopLifecycleController(), observer),
      )

      val run = async { bootable.run() }
      runCurrent()
      assert(events == listOf("start pool", "start server"))
      assert(observer.states() == mapOf("pool" to ServiceState.Running, "server" to ServiceState.Running))
      assert(run.isActive)

      bootable.stop(3)
      // only the first stop counts
      bootable.stop(4)
      assert(run.await() == 3)
      assert(events.drop(2) == listOf("stop server", "stopped server", "stop pool", "stopped pool"))
      assert(observer.states() == mapOf("pool" to ServiceState.Stopped, "server" to ServiceState.Stopped))
    }
  }

  test("a stop asked for during the start, on the thread that runs the application, stops it with its exit code") {
    runTest {
      val events = events()
      val bootable = Bootable(
        setOf(RecordingService("pool", Infrastructure, events)),
        // the stop during the start means the application is never running, so its appRunning isn't called
        setOf(AppStartStopLifecycleController(), StopsAtStart(), Around("around", 0, events)),
      )

      assert(bootable.run() == 5)
      assert(events == listOf("around{", "around(", "start pool", ")around", "}around", "stop pool", "stopped pool"))
    }
  }

  test("a stop asked for before the application runs stops it before anything starts") {
    runTest {
      val events = events()
      val bootable = Bootable(setOf(RecordingService("pool", Infrastructure, events)), setOf(AppStartStopLifecycleController()))

      bootable.stop(7)
      assert(bootable.run() == 7)
      assert(events.isEmpty())
    }
  }

  test("a stop during the start cancels the start, and stops the services that started") {
    runTest {
      val events = events()
      val observer = Observer()
      val bootable = Bootable(
        setOf(
          RecordingService("pool", Infrastructure, events),
          RecordingService("migrate", Initialization, events, startTime = 1.hours),
          RecordingService("server", Serving, events),
        ),
        setOf(AppStartStopLifecycleController(), observer),
      )

      val run = async { bootable.run() }
      advanceTimeBy(1.seconds)
      assert(events == listOf("start pool", "start migrate"))
      bootable.stop()
      assert(run.await() == 0)
      assert(events == listOf("start pool", "start migrate", "stop pool", "stopped pool"))
      assert(observer.states()["migrate"] == ServiceState.Pending)
      assert(currentTime < 1.hours.inWholeMilliseconds)
    }
  }

  test("a service whose background work fails stops the application in order, with exit code 1") {
    runTest {
      val events = events()
      val observer = Observer()
      val background = BackgroundService("worker", events, StandardTestDispatcher(testScheduler))
      val bootable = Bootable(
        setOf(RecordingService("pool", Infrastructure, events), background),
        setOf(AppStartStopLifecycleController(), observer),
      )

      val run = async { bootable.run() }
      runCurrent()
      assert(events == listOf("start pool", "start worker"))
      background.failure.complete(Unit)
      assert(run.await() == 1)
      assert(events.drop(2) == listOf("stop worker", "stop pool", "stopped pool"))
      assert(observer.states() == mapOf("pool" to ServiceState.Stopped, "worker" to ServiceState.Stopped))
    }
  }

  test("a service whose scope failure stops the application with an exit code of its own stops with that code") {
    runTest {
      val background = BackgroundService(
        "worker", events(), StandardTestDispatcher(testScheduler), ScopeFailurePolicy.StopApplication(exitCode = 3),
      )
      val bootable = Bootable(setOf(background), setOf(AppStartStopLifecycleController()))

      val run = async { bootable.run() }
      runCurrent()
      background.failure.complete(Unit)
      assert(run.await() == 3)
    }
  }

  test("a service whose scope failure is to log and continue carries on, with its other coroutines, when one fails") {
    runTest {
      val events = events()
      val observer = Observer()
      val background =
        BackgroundService("worker", events, StandardTestDispatcher(testScheduler), ScopeFailurePolicy.LogAndContinue)
      val bootable = Bootable(setOf(background), setOf(AppStartStopLifecycleController(), observer))

      val run = async { bootable.run() }
      runCurrent()
      background.failure.complete(Unit)
      runCurrent()
      assert(background.job!!.isCancelled)
      assert(background.sibling!!.isActive)
      assert(run.isActive)
      assert(observer.states() == mapOf("worker" to ServiceState.Running))

      bootable.stop()
      assert(run.await() == 0)
      assert(background.sibling!!.isCancelled)
      assert(events == listOf("start worker", "stop worker"))
    }
  }

  test("a service's scope is cancelled once it has stopped") {
    runTest {
      val background = BackgroundService("worker", events(), StandardTestDispatcher(testScheduler))
      val bootable = Bootable(setOf(background), setOf(AppStartStopLifecycleController()))

      val run = async { bootable.run() }
      runCurrent()
      assert(background.job!!.isActive)
      bootable.stop()
      assert(run.await() == 0)
      assert(background.job!!.isCancelled)
    }
  }

  test("a stop that outlasts the shutdown timeout is cancelled, the rest skipped, and the exit code is 1") {
    runTest {
      val events = events()
      val observer = Observer()
      val bootable = Bootable(
        setOf(RecordingService("pool", Infrastructure, events), RecordingService("server", Serving, events, stopTime = 1.hours)),
        setOf(AppStartStopLifecycleController(), observer),
      )

      val run = async { bootable.run(maxShutdownTime = 10.seconds) }
      runCurrent()
      bootable.stop()
      assert(run.await() == 1)
      assert(currentTime == 10.seconds.inWholeMilliseconds)
      assert(events == listOf("start pool", "start server", "stop server"))
      assert(observer.states() == mapOf("pool" to ServiceState.Running, "server" to ServiceState.Stopping))
    }
  }

  test("cancelling the run stops the services, then rethrows") {
    runTest {
      val events = events()
      val bootable = Bootable(setOf(RecordingService("pool", Infrastructure, events)), setOf(AppStartStopLifecycleController()))

      val run = launch { bootable.run() }
      runCurrent()
      run.cancelAndJoin()
      assert(run.isCancelled)
      assert(events == listOf("start pool", "stop pool", "stopped pool"))
    }
  }

  test("a Bootable runs once") {
    runTest {
      val bootable = Bootable(emptySet(), setOf(AppStartStopLifecycleController()))
      bootable.stop()
      assert(bootable.run() == 0)
      assert(runCatching { bootable.run() }.exceptionOrNull() is IllegalStateException)
    }
  }

  test("the watchdog halts a stop that blocks in code that ignores cancellation, after the timeout and its grace") {
    val blocking = object : AppService {
      override suspend fun shutdown() = Thread.sleep(2_000)
    }
    val bootable = Bootable(setOf(blocking), setOf(AppStartStopLifecycleController(), StopsAtStart()))
    val halted = CompletableDeferred<Int>()

    runBlocking {
      val started = TimeSource.Monotonic.markNow()
      bootable.run(
        StartupTimeline(started), maxShutdownTime = 100.milliseconds, halt = { halted.complete(it) },
        haltGrace = 100.milliseconds,
      )
      assert(halted.isCompleted && halted.await() == 1)
    }
  }

  test("the default lifecycle controllers include a health controller, which another replaces") {
    val defaults = defaultLifecycleControllers()
    assert(defaults.filterIsInstance<HealthLifecycleController>().size == 1)

    val health = HealthLifecycleController()
    val controllers = defaults.withHealthLifecycleController(health)
    assert(controllers.filterIsInstance<HealthLifecycleController>() == listOf(health))
    // the others are kept
    assert(controllers - health == defaults.filterNot { it is HealthLifecycleController }.toSet())
    // and a set without one gains it
    assert(emptySet<LifecycleController>().withHealthLifecycleController(health) == setOf(health))
  }
}
