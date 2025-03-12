package com.github.rocketraman.bootable.logging.log4j2

import com.github.rocketraman.bootable.logging.BootLoggingLevel
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.io.IoBuilder
import org.apache.logging.log4j.kotlin.logger

private var loggingIsInitialized = false

/**
 * Basic setup that should be done at application initialization time, before anything else is done, such
 * as setting system properties or configuring logging. Call this before the application dependency graph is
 * created via DI, or any loggers are created or called.
 *
 * The default is to redirect standard out and error to the logging system, and to use the logging output type
 * set in the environment via "LOGGING_TYPE", or to fall back to the default logging type if that is not set
 * (currently traditional text log formatting).
 *
 * CLI tools can use this too, but should specify [redirectStandardOutErr] as `false`, and perhaps should
 * set a specific [LoggingType] as well.
 */
fun loggingInit() = loggingInit(true, null as LoggingType?)

@Deprecated("Use loggingInit with enumerated LoggingType",
  ReplaceWith(
    expression = "loggingInit(redirectStandardOutErr, LoggingType.valueOfOrNull(loggingType))",
    imports = ["com.github.rocketraman.bootable.logging.log4j2.LoggingType"]
  )
)
fun loggingInit(redirectStandardOutErr: Boolean = true, loggingType: String? = null) =
  loggingInit(redirectStandardOutErr, loggingType?.let { LoggingType.valueOfOrNull(it) })

/**
 * Basic setup that should be done at application initialization time, before anything else is done, such
 * as setting system properties or configuring logging. Call this before the application dependency graph is
 * created via DI, or any loggers are created or called.
 *
 * CLI tools can use this too, but should specify [redirectStandardOutErr] as `false`.
 *
 * Allow merging specifies whether a log4j2.xml file should be merged with the base configuration file. This
 * is true by default and allows projects to specify their own log4j2.xml files that are merged with the base
 * configuration.
 *
 * Merging is done in the following order:
 * * Base configuration from Bootable
 * * Configuration provided by `log4j2-<loggingtype>.xml` e.g. `log4j2-color.xml`
 * * Configuration provided by `log4j2.xml`
 */
@OptIn(ExperimentalStdlibApi::class)
fun loggingInit(
  redirectStandardOutErr: Boolean = true,
  loggingType: LoggingType? = null,
  allowMerging: Boolean = true,
  overrideLoggingLevel: BootLoggingLevel? = null,
) {
  // prevent double initialization -- this isn't meant to be thread-safe, initialization should occur immediately
  // on startup before anything else is done, also this method is idempotent except that the logging occurs twice
  if (loggingIsInitialized) return

  loggingIsInitialized = true

  fun setSystemPropIfNotSet(prop: String, value: String) {
    if(System.getProperty(prop) == null) System.setProperty(prop, value)
  }

  // JUL should use log4j2
  setSystemPropIfNotSet("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager")

  if (overrideLoggingLevel != null) {
    setSystemPropIfNotSet("logging.level", overrideLoggingLevel.name)
  }

  val envLoggingType = System.getenv("LOGGING_TYPE")

  val warnings = mutableListOf<String>()

  // passed in value takes precedence, then env var, then default
  val configuredLoggingType = loggingType
    ?: envLoggingType?.let { envType ->
      LoggingType.valueOfOrNull(envType).also { resolvedEnvType ->
        if (resolvedEnvType == null) {
          warnings.add(
            "Invalid LOGGING_TYPE environment value '$envType', " +
              "expected one of ${LoggingType.entries.joinToString { it.name }}, " +
              "continuing with default logging type"
          )
        }
      }
    }
    ?: LoggingType.DEFAULT

  setSystemPropIfNotSet("log4j.configurationFile", when(configuredLoggingType) {
    LoggingType.COLOR, LoggingType.DEFAULT -> "log4j2-base.xml"
    LoggingType.PLAIN -> "log4j2-base-plain.xml"
    LoggingType.JSON -> "log4j2-base-json.xml"
    LoggingType.GCLOUD -> "log4j2-base-json-gcloud.xml"
    // TODO test this
    LoggingType.EMPTY -> "log4j2-base-empty.xml"
  }.let { if (allowMerging) "$it,log4j2-${configuredLoggingType.name.lowercase()}.xml,log4j2.xml" else it })

  // redirect std out and err to the logger (for third party libs that are not good standard out/err citizens)
  if(redirectStandardOutErr) {
    System.setOut(IoBuilder.forLogger(LogManager.getLogger("STDOUT")).setLevel(Level.INFO).buildPrintStream())
    System.setErr(IoBuilder.forLogger(LogManager.getLogger("STDERR")).setLevel(Level.WARN).buildPrintStream())
  }

  val initLogger = logger("com.github.rocketraman.bootable.logging.log4j2.loggingInit")

  warnings.forEach { w ->
    initLogger.warn(w)
  }

  initLogger.debug { "Logger initialization complete, stdout/err redirection = $redirectStandardOutErr" }
}
