package com.github.rocketraman.bootable.logging.log4j2

enum class LoggingType {
  PLAIN,
  JSON,
  GCLOUD,
  DEFAULT,
  ;

  companion object {
    fun valueOfOrNull(value: String): LoggingType? = values().firstOrNull { it.name == value.uppercase() }
  }
}
