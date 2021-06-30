package com.github.rocketraman.bootable.logging.log4j2

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.io.IoBuilder
import org.apache.logging.log4j.kotlin.logger

/**
 * Basic setup that should be done at application initialization time, before anything else is done, such
 * as setting system properties or configuring logging. Call this before the application dependency graph is
 * created via DI, or any loggers are created or called.
 *
 * CLI tools can use this too, but should specify [redirectStandardOutErr] as `false`.
 */
fun loggingInit(redirectStandardOutErr: Boolean = true, loggingType: String? = null) {
  fun setSystemPropIfNotSet(prop: String, value: String) {
    if(System.getProperty(prop) == null) System.setProperty(prop, value)
  }

  // JUL should use log4j2
  setSystemPropIfNotSet("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager")

  val envLoggingType = System.getenv("LOGGING_TYPE")?.lowercase()
  setSystemPropIfNotSet("log4j.configurationFile", when {
    envLoggingType ?: loggingType == "json" ->
      "log4j2-base-json.xml,log4j2.xml"
    envLoggingType ?: loggingType == "gcloud" ->
      "log4j2-base-json-gcloud.xml,log4j2.xml"
    else ->
      "log4j2-base.xml,log4j2.xml"
  })

  // redirect std out and err to the logger (for third party libs that are not good standard out/err citizens)
  if(redirectStandardOutErr) {
    System.setOut(IoBuilder.forLogger(LogManager.getLogger("STDOUT")).setLevel(Level.INFO).buildPrintStream())
    System.setErr(IoBuilder.forLogger(LogManager.getLogger("STDERR")).setLevel(Level.WARN).buildPrintStream())
  }

  logger("com.github.rocketraman.bootable.logging.log4j2.loggingInit")
    .debug { "Logger initialization complete, stdout/err redirection = $redirectStandardOutErr" }
}
