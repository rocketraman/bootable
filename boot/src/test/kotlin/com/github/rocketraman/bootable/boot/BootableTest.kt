package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.boot.DefaultPhase.Application
import com.github.rocketraman.bootable.boot.DefaultPhase.Infrastructure
import com.github.rocketraman.bootable.boot.DefaultPhase.Initialization
import com.github.rocketraman.bootable.boot.DefaultPhase.Serving
import de.infix.testBalloon.framework.core.testSuite
import kotlin.time.TimeMark

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
 * Records its code around the application's start, as `name{` and `}name`, and around each service's start, as `name(`
 * and `)name`.
 */
private class Around(
  private val name: String,
  private val priority: Int,
  private val events: MutableList<String>,
) : LifecycleController {
  override fun priority() = priority

  override fun aroundAppStart(serviceContext: ServiceContext, startupMark: TimeMark, start: () -> Unit) {
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
}
