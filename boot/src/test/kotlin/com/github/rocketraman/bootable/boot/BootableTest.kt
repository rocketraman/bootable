package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.boot.DefaultPhase.Application
import com.github.rocketraman.bootable.boot.DefaultPhase.Infrastructure
import com.github.rocketraman.bootable.boot.DefaultPhase.Initialization
import com.github.rocketraman.bootable.boot.DefaultPhase.Serving
import de.infix.testBalloon.framework.core.testSuite
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private class RecordingService(
  private val name: String,
  private val phase: Phase,
  private val events: MutableList<String>,
  private val failStart: Boolean = false,
) : AppService {
  override fun name() = name
  override fun ordering() = Ordering(phase)

  override fun start() {
    events += "start $name"
    check(!failStart) { "$name failed to start" }
  }

  override fun shutdown() {
    events += "stop $name"
  }
}

/** Votes against starting [name]. */
private class Veto(private val name: String) : LifecycleController {
  override fun serviceStartVote(appService: AppService) =
    if (appService.name() == name) ServiceStartVote.NO else ServiceStartVote.YES
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

  override fun aroundAppStart(serviceContext: ServiceContext, timeline: StartupTimeline, start: () -> Unit) {
    events += "$name{"
    try {
      start()
    } finally {
      events += "}$name"
    }
  }

  override fun aroundServiceStart(appService: AppService, start: () -> Unit) {
    events += "$name("
    try {
      start()
    } finally {
      events += ")$name"
    }
  }

  override fun appRunning() {
    events += "$name!"
  }
}

/** Stops the application once it is running, as `stopping`, with the lowest priority, so after the others' [appRunning]. */
private class StopsWhenRunning(private val events: MutableList<String>) : LifecycleController {
  private lateinit var stop: (Int) -> Unit

  override fun appStart(serviceContext: ServiceContext, normalStop: (exitCode: Int) -> Unit) {
    stop = normalStop
  }

  override fun appRunning() {
    events += "stopping"
    stop(0)
  }

  override fun priority() = Int.MIN_VALUE
}

/** Stops the application as it starts, before Bootable waits for a stop, as a signal during the start would. */
private class StopsAtStart : LifecycleController {
  override fun appStart(serviceContext: ServiceContext, normalStop: (exitCode: Int) -> Unit) = normalStop(5)
}

val BootableTest by testSuite {
  test("a failed start stops the services that started, in reverse, but not the one that failed") {
    val events = mutableListOf<String>()
    val bootable = Bootable(
      setOf(
        RecordingService("pool", Infrastructure, events),
        RecordingService("skipped", Infrastructure, events),
        RecordingService("migrate", Initialization, events, failStart = true),
        RecordingService("app", Application, events),
        RecordingService("server", Serving, events),
      ),
      setOf(AppStartStopLifecycleController(), Veto("skipped")),
    )

    // a long shutdown time, so the controller's forced-exit timer can't fire while the tests run
    assert(bootable.run(maxShutdownTime = 1_000_000) == -1)
    assert(events == listOf("start pool", "start migrate", "stop pool"))
  }

  test("controllers run around the start and each service's, the highest priority outermost, and see its failure") {
    val events = mutableListOf<String>()
    val bootable = Bootable(
      setOf(
        RecordingService("pool", Infrastructure, events),
        RecordingService("skipped", Infrastructure, events),
        RecordingService("migrate", Initialization, events, failStart = true),
      ),
      setOf(AppStartStopLifecycleController(), Veto("skipped"), Around("inner", 1, events), Around("outer", 2, events)),
    )

    assert(bootable.run(maxShutdownTime = 1_000_000) == -1)
    assert(
      events == listOf(
        "outer{", "inner{",
        "outer(", "inner(", "start pool", ")inner", ")outer",
        "outer(", "inner(", "start migrate", ")inner", ")outer",
        "}inner", "}outer",
        "stop pool",
      )
    )
  }

  test("once the whole start has completed, the controllers are told the application is running, highest first") {
    val events = mutableListOf<String>()
    val bootable = Bootable(
      setOf(RecordingService("pool", Infrastructure, events)),
      setOf(AppStartStopLifecycleController(), Around("inner", 1, events), Around("outer", 2, events), StopsWhenRunning(events)),
    )

    assert(bootable.run(maxShutdownTime = 1_000_000) == 0)
    assert(
      events == listOf(
        "outer{", "inner{", "outer(", "inner(", "start pool", ")inner", ")outer", "}inner", "}outer",
        "outer!", "inner!", "stopping",
        "stop pool",
      )
    )
  }

  test("a controller that fails when the application is running fails the start, and the controllers after it aren't told") {
    val events = mutableListOf<String>()
    val failing = object : LifecycleController {
      override fun appRunning() = error("not running after all")
      override fun priority() = 2
    }
    val bootable = Bootable(
      setOf(RecordingService("pool", Infrastructure, events)),
      setOf(AppStartStopLifecycleController(), failing, Around("lower", 1, events)),
    )

    assert(bootable.run(maxShutdownTime = 1_000_000) == -1)
    assert(events == listOf("lower{", "lower(", "start pool", ")lower", "}lower", "stop pool"))
  }

  test("the controllers get the timeline of the boot, with the planning and initialization added to its stages") {
    var captured: StartupTimeline? = null
    val capture = object : LifecycleController {
      override fun aroundAppStart(serviceContext: ServiceContext, timeline: StartupTimeline, start: () -> Unit) {
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

    assert(bootable.run(booted, maxShutdownTime = 1_000_000) == -1)
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

  test("a stop asked for before the application waits for one stops it with its exit code, rather than blocking") {
    val events = mutableListOf<String>()
    val bootable = Bootable(
      setOf(RecordingService("pool", Infrastructure, events)),
      // the stop during the start means the application is never running, so its appRunning isn't called
      setOf(AppStartStopLifecycleController(), StopsAtStart(), Around("around", 0, events)),
    )

    // the stop is asked for on the thread that runs the application, which a hand-off to that thread would block forever
    var exitCode: Int? = null
    val runner = kotlin.concurrent.thread { exitCode = bootable.run(maxShutdownTime = 1_000_000) }
    runner.join(10_000)
    assert(!runner.isAlive)
    assert(exitCode == 5)
    assert(events == listOf("around{", "around(", "start pool", ")around", "}around", "stop pool"))
  }
}
