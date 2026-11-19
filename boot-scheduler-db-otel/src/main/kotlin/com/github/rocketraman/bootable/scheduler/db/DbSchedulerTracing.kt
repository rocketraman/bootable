package com.github.rocketraman.bootable.scheduler.db

import com.github.kagkarlsson.scheduler.SchedulerBuilder
import com.github.kagkarlsson.scheduler.event.ExecutionChain
import com.github.kagkarlsson.scheduler.event.ExecutionInterceptor
import com.github.kagkarlsson.scheduler.task.CompletionHandler
import com.github.kagkarlsson.scheduler.task.ExecutionComplete
import com.github.kagkarlsson.scheduler.task.ExecutionContext
import com.github.kagkarlsson.scheduler.task.ExecutionOperations
import com.github.kagkarlsson.scheduler.task.TaskInstance
import com.github.rocketraman.bootable.telemetry.otel.ALWAYS_SAMPLE
import com.github.rocketraman.bootable.telemetry.otel.Sample
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

/** The name of the db-scheduler task an execution's span belongs to. */
val DB_SCHEDULER_TASK: AttributeKey<String> = AttributeKey.stringKey("bootable.db_scheduler.task")

/** The id of the task instance an execution's span runs. */
val DB_SCHEDULER_INSTANCE: AttributeKey<String> = AttributeKey.stringKey("bootable.db_scheduler.instance")

/** How many executions of the task instance in a row failed before this one. */
val DB_SCHEDULER_CONSECUTIVE_FAILURES: AttributeKey<Long> =
  AttributeKey.longKey("bootable.db_scheduler.consecutive_failures")

/**
 * A [DbSchedulerCustomizer] that traces each execution of a db-scheduler task through [openTelemetry], with an
 * `ExecutionInterceptor`: each execution starts a trace of its own, with a root span named for the task, such as
 * `db-scheduler send-reminder`, so that the spans within it, such as its JDBC statements, and its log events, belong
 * to it. An execution that fails ends its span with an error status and the exception recorded.
 *
 * The span also covers the execution's completion, such as the statement that deletes a one-time execution's row or
 * reschedules a recurring one, so that statement belongs to the trace too. A failed execution's span ends as it fails,
 * before db-scheduler's failure handler reschedules it.
 *
 * Only executions are traced: db-scheduler's own statements, such as its polls for due executions and its
 * heartbeats, run outside any execution, and so, through a traced `DataSource`, each is still a trace of its own.
 *
 * Give it to [DbSchedulerService]:
 *
 * ```
 * DbSchedulerService(database.dataSource, config, tasks, customizer = dbSchedulerTracing(telemetry.openTelemetry))
 * ```
 *
 * [sample] is whether each execution's trace is sampled as the SDK's sampler decides, the default, or always, for
 * tasks that run rarely enough that every execution's trace is worth keeping.
 */
fun dbSchedulerTracing(openTelemetry: OpenTelemetry, sample: Sample = Sample.BySampler): DbSchedulerCustomizer {
  val interceptor = TracingExecutionInterceptor(openTelemetry.getTracer(INSTRUMENTATION_SCOPE), sample)
  return object : DbSchedulerCustomizer {
    override fun customize(builder: SchedulerBuilder) {
      builder.addExecutionInterceptor(interceptor)
    }

    override fun toString() = "dbSchedulerTracing"
  }
}

private class TracingExecutionInterceptor(private val tracer: Tracer, private val sample: Sample) : ExecutionInterceptor {
  override fun execute(
    taskInstance: TaskInstance<*>,
    executionContext: ExecutionContext,
    chain: ExecutionChain,
  ): CompletionHandler<*> {
    val span = tracer.spanBuilder("db-scheduler ${taskInstance.taskName}")
      .setNoParent()
      .setSpanKind(SpanKind.INTERNAL)
      .setAttribute(DB_SCHEDULER_TASK, taskInstance.taskName)
      .setAttribute(DB_SCHEDULER_INSTANCE, taskInstance.id)
      .setAttribute(DB_SCHEDULER_CONSECUTIVE_FAILURES, executionContext.execution.consecutiveFailures.toLong())
      .apply { if (sample == Sample.Always) setAttribute(ALWAYS_SAMPLE, true) }
      .startSpan()
    val completion = try {
      span.makeCurrent().use { chain.proceed(taskInstance, executionContext) }
    } catch (e: Throwable) {
      // db-scheduler hands the failure to the task's failure handler, outside this interceptor
      span.fail(e)
      span.end()
      throw e
    }
    // db-scheduler completes the execution, such as deleting its row, once this returns, on the same thread: keep the
    // span open, and current, until then, so the completion's statements belong to the execution's trace
    @Suppress("UNCHECKED_CAST")
    return SpanCompletionHandler(span, completion as CompletionHandler<Any?>)
  }
}

private class SpanCompletionHandler(
  private val span: Span,
  private val delegate: CompletionHandler<Any?>,
) : CompletionHandler<Any?> {
  override fun complete(executionComplete: ExecutionComplete, executionOperations: ExecutionOperations<Any?>) {
    try {
      span.makeCurrent().use { delegate.complete(executionComplete, executionOperations) }
    } catch (e: Throwable) {
      span.fail(e)
      throw e
    } finally {
      span.end()
    }
  }
}

private fun Span.fail(e: Throwable) {
  recordException(e)
  setStatus(StatusCode.ERROR)
}

private const val INSTRUMENTATION_SCOPE = "com.github.rocketraman.bootable.scheduler.db"
