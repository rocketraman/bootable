package com.github.rocketraman.bootable.test

import com.github.rocketraman.bootable.config.config
import de.infix.testBalloon.framework.core.testSuite

private data class ServerConfig(val hostPort: String)

val StaticConfigBinderTest by testSuite {
  test("gives each section at its prefix") {
    val server = ServerConfig("127.0.0.1:0")
    val binder = StaticConfigBinder("server" to server, "other" to ServerConfig(":0"))
    assert(binder.config<ServerConfig>("server") === server)
  }

  test("fails for a prefix it has no section at, naming it") {
    val failure = runCatching { StaticConfigBinder("server" to ServerConfig(":0")).config<ServerConfig>("client") }
    assert(failure.exceptionOrNull()?.message?.contains("No config at client") == true)
  }

  test("fails for a section of another type, naming the prefix") {
    val failure = runCatching { StaticConfigBinder("server" to "text").config<ServerConfig>("server") }
    assert(failure.exceptionOrNull()?.message?.contains("The config at server is a String") == true)
  }

  test("takes only one section for each prefix") {
    assert(runCatching { StaticConfigBinder("server" to ServerConfig(":0"), "server" to ServerConfig(":1")) }.isFailure)
  }
}
