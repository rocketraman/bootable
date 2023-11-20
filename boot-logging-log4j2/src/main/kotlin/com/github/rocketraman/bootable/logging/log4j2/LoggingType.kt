package com.github.rocketraman.bootable.logging.log4j2

enum class LoggingType {
  // plaintext logging types
  COLOR,
  PLAIN,
  DEFAULT,
  // json types
  /** Note that the JSON type requires the implementor to provide Jackson on the runtime classpath. */
  JSON,
  GCLOUD,
  // no logging
  EMPTY,
  ;

  companion object {
    fun valueOfOrNull(value: String): LoggingType? = entries.firstOrNull { it.name == value.uppercase() }
  }
}
