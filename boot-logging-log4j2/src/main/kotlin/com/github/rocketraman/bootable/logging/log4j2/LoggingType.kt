package com.github.rocketraman.bootable.logging.log4j2

enum class LoggingType {
  // plaintext logging types
  COLOR,
  PLAIN,
  DEFAULT,
  // json types
  JSON,
  GCLOUD,
  // no logging
  EMPTY,
  ;

  companion object {
    @OptIn(ExperimentalStdlibApi::class)
    fun valueOfOrNull(value: String): LoggingType? = entries.firstOrNull { it.name == value.uppercase() }
  }
}
