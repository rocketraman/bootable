package com.github.rocketraman.bootable.test

import com.github.rocketraman.bootable.logging.log4j2.LoggingType
import com.github.rocketraman.bootable.logging.log4j2.loggingInit
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.apache.logging.log4j.core.config.LoggerConfig
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.message.MapMessage
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Initializes Bootable's logging for a test, as `boot` does for an application: Bootable's base Log4j2 configuration,
 * merged with the test's `log4j2.xml`, if it has one, at the level of `LOGGING_LEVEL`, or `logging.level`, or INFO.
 *
 * Standard out and error are not redirected to the logs, so the test framework's output is left alone. [loggingType]
 * is as `boot`'s, and defaults to the `LOGGING_TYPE` environment variable, or else the default type.
 *
 * [LogCapture] calls it, so a test that only captures needn't. If Log4j2 has already started, because something logged
 * first, it is reconfigured. Only the first call has any effect.
 */
fun testLoggingInit(loggingType: LoggingType? = null) {
  if (initialized) return
  initialized = true
  loggingInit(redirectStandardOutErr = false, loggingType = loggingType)
  // if something logged before the configuration files were set, Log4j2 started without them: with its default, or
  // with the test's `log4j2.xml` alone, which may only make sense merged with Bootable's base configuration
  val context = LogManager.getContext(false) as LoggerContext
  context.reconfigure(ConfigurationFactory.getInstance().getConfiguration(context, context.name, null as URI?))
}

@Volatile private var initialized = false

/**
 * Captures the Log4j2 events logged to [loggerName] and its descendants, the root logger by default, from when it is
 * created until it is closed:
 *
 * ```
 * LogCapture("org.example").use { logs ->
 *   service.handle(request)
 *   val event = logs.single(Level.INFO) { "Stored entry" in it.message.formattedMessage }
 *   assert(event.fields["entry"] == "greeting")
 * }
 * ```
 *
 * It captures what the logger's level lets through. Give a [level] to lower it while capturing, such as to DEBUG, for
 * the events a test needs whatever the configuration: the logger's other appenders then get those events too.
 *
 * Each event is an immutable copy of Log4j2's [LogEvent], so its message, level, logger, thrown, and context data,
 * such as the trace and span ids that `boot-telemetry-otel` puts there, are all there to assert on.
 */
class LogCapture(
  private val loggerName: String = LogManager.ROOT_LOGGER_NAME,
  level: Level? = null,
) : AutoCloseable {
  private val captured = CopyOnWriteArrayList<LogEvent>()
  private val context = LogManager.getContext(false) as LoggerContext
  private val appender = object : AbstractAppender("capture-${System.identityHashCode(this)}", null, null, true, Property.EMPTY_ARRAY) {
    // copied at once: Log4j2 may reuse a mutable event once this returns
    override fun append(event: LogEvent) {
      captured += event.toImmutable()
    }
  }
  private val loggerConfig: LoggerConfig
  /** The logger config created for [loggerName], if it had none of its own, to remove on close. */
  private val created: Boolean
  private val previousLevel: Level

  init {
    testLoggingInit()
    val configuration = context.configuration
    val existing = configuration.getLoggerConfig(loggerName)
    created = existing.name != loggerName
    loggerConfig = if (created) {
      LoggerConfig(loggerName, existing.level, true).also { configuration.addLogger(loggerName, it) }
    } else {
      existing
    }
    previousLevel = loggerConfig.level
    if (level != null && level.isLessSpecificThan(previousLevel)) loggerConfig.level = level
    appender.start()
    loggerConfig.addAppender(appender, null, null)
    context.updateLoggers()
  }

  /** Every event captured so far, in the order they were logged. */
  val events: List<LogEvent> get() = captured.toList()

  /** The events at [level], or at any level if it is null, that match [predicate]. */
  fun events(level: Level? = null, predicate: (LogEvent) -> Boolean = { true }): List<LogEvent> =
    captured.filter { (level == null || it.level == level) && predicate(it) }

  /** The formatted messages of the events at [level], or at any level if it is null. */
  fun messages(level: Level? = null): List<String> = events(level).map { it.message.formattedMessage }

  /**
   * The one event at [level], or at any level if it is null, that matches [predicate]. Throws an [AssertionError] that
   * lists what was captured if there is none, or more than one.
   */
  fun single(level: Level? = null, predicate: (LogEvent) -> Boolean = { true }): LogEvent {
    val matching = events(level, predicate)
    if (matching.size != 1) {
      throw AssertionError(
        "Expected one${level?.let { " $it" }.orEmpty()} event to match, but ${matching.size} did, of:\n" + describe()
      )
    }
    return matching.single()
  }

  /** What was captured, one event per line, e.g. for an assertion's message. */
  fun describe(): String = captured.joinToString("\n") {
    "  ${it.level} ${it.loggerName}: ${it.message.formattedMessage}" + it.fields.takeIf { f -> f.isNotEmpty() }?.let { f -> " $f" }.orEmpty()
  }

  override fun close() {
    loggerConfig.removeAppender(appender.name)
    if (created) {
      context.configuration.removeLogger(loggerName)
    } else {
      loggerConfig.level = previousLevel
    }
    context.updateLoggers()
    appender.stop()
  }
}

/**
 * Runs [block], and returns what was logged to [loggerName] meanwhile, as a [LogCapture] captures it: closed, so it
 * captures nothing more, with its events, and its helpers, to assert on.
 */
inline fun captureLogs(
  loggerName: String = LogManager.ROOT_LOGGER_NAME,
  level: Level? = null,
  block: () -> Unit,
): LogCapture = LogCapture(loggerName, level).also { logs -> logs.use { block() } }

/**
 * The structured fields of this event's message, if it is a [MapMessage], such as a `StringMapMessage` or a
 * `StructuredDataMessage`: empty otherwise. Context data, such as a trace id, is in [LogEvent.getContextData].
 */
val LogEvent.fields: Map<String, String>
  get() = (message as? MapMessage<*, *>)?.data?.mapValues { it.value.toString() }.orEmpty()
