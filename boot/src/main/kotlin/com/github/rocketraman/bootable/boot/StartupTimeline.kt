package com.github.rocketraman.bootable.boot

import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

/**
 * The application's boot before its services start: when it began, [start], and the [stages] it went through since,
 * in order, each ending where the next begins. A [LifecycleController] gets it in
 * [LifecycleController.aroundAppStart], for example to record the stages as spans of the start's trace.
 *
 * With [boot], [start] is when `boot` was called, and the stages are:
 * - [INITIALIZE_JVM_CLASSES]: initializing logging, then the `bootable` lambda, which builds the application, such as
 *   decoding its config, building its DI graph, and constructing its services. On a cold JVM, most of this stage is
 *   loading and initializing the classes of the logging, config, telemetry and database libraries, which a JVM AOT
 *   cache can cut sharply;
 * - [PLAN_START]: computing the start plan;
 * - [INITIALIZE]: every controller's [LifecycleController.appInit].
 *
 * A [Bootable] run without [boot] has only the last two, from when it began running.
 *
 * The marks are of the JVM's monotonic clock, so the stages' durations aren't affected by changes to the wall clock.
 */
class StartupTimeline(
  val start: ComparableTimeMark,
  val stages: List<StartupStage> = emptyList(),
) {
  /** Where the next stage begins: the end of the last, or else [start]. */
  private val last: ComparableTimeMark get() = stages.lastOrNull()?.end ?: start

  /** This timeline, with a stage named [name] from the end of the last until now. */
  internal fun then(name: String, end: ComparableTimeMark = TimeSource.Monotonic.markNow()) =
    StartupTimeline(start, stages + StartupStage(name, last, end))

  companion object {
    const val INITIALIZE_JVM_CLASSES = "initialize JVM classes for logging and configuration"
    const val PLAN_START = "plan start"
    const val INITIALIZE = "initialize"
  }
}

/** A stage of the application's boot, named [name], from [start] to [end]. */
class StartupStage(val name: String, val start: ComparableTimeMark, val end: ComparableTimeMark) {
  override fun toString() = "$name (${end - start})"
}
