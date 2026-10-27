package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.boot.DefaultPhase.Application
import com.github.rocketraman.bootable.boot.DefaultPhase.Infrastructure
import com.github.rocketraman.bootable.boot.DefaultPhase.Initialization
import com.github.rocketraman.bootable.boot.DefaultPhase.Serving
import de.infix.testBalloon.framework.core.testSuite

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
}
