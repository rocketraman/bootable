package com.github.rocketraman.bootable.env

interface EnvironmentConfig {
  val environment: String
}

sealed class Env {
  data object Dev : Env()
  data object Test : Env()
  data object Prod : Env()
  data class Custom(val name: String) : Env()

  companion object {
    fun fromString(s: String) = when (s.lowercase()) {
      "dev" -> Dev
      "test" -> Test
      "prod" -> Prod
      else -> Custom(s)
    }
  }
}
