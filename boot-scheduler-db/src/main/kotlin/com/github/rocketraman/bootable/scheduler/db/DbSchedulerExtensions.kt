package com.github.rocketraman.bootable.scheduler.db

import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.kagkarlsson.scheduler.task.FailureHandler
import com.github.kagkarlsson.scheduler.task.TaskInstanceId
import com.github.kagkarlsson.scheduler.task.SchedulableInstance
import com.github.kagkarlsson.scheduler.task.TaskInstance
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaDuration
import kotlin.time.toJavaInstant

/** Schedules [taskInstance] to run at [executionTime]. */
fun <T> SchedulerClient.schedule(taskInstance: TaskInstance<T>, executionTime: Instant) =
  schedule(taskInstance, executionTime.toJavaInstant())

/** Schedules [taskInstance] to run at [executionTime], unless it is already scheduled; returns whether it was. */
fun <T> SchedulerClient.scheduleIfNotExists(taskInstance: TaskInstance<T>, executionTime: Instant): Boolean =
  scheduleIfNotExists(taskInstance, executionTime.toJavaInstant())

/** Moves the execution of [taskInstanceId] to [executionTime]; returns whether there was one. */
fun SchedulerClient.reschedule(taskInstanceId: TaskInstanceId, executionTime: Instant): Boolean =
  reschedule(taskInstanceId, executionTime.toJavaInstant())

/** This task instance, to run at [executionTime]. */
fun <T> TaskInstance.Builder<T>.scheduledTo(executionTime: Instant): SchedulableInstance<T> =
  scheduledTo(executionTime.toJavaInstant())

/** A schedule of every [interval], from the end of the last execution. */
fun fixedDelay(interval: Duration): FixedDelay = FixedDelay.of(interval.toJavaDuration())

/** Retries a failed execution after [delay], and after [rate] times as long each time it fails again. */
fun <T> exponentialBackoff(delay: Duration, rate: Double = 1.5): FailureHandler<T> =
  FailureHandler.ExponentialBackoffFailureHandler(delay.toJavaDuration(), rate)
