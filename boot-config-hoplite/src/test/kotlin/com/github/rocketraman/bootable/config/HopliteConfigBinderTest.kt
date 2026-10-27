package com.github.rocketraman.bootable.config

import com.sksamuel.hoplite.ConfigException
import de.infix.testBalloon.framework.core.testSuite

// public: hoplite constructs it reflectively
data class ServerConfig(val host: String, val port: Int)

enum class Speed { Fast, Slow }

// public: hoplite constructs it reflectively
data class SpeedConfig(val speed: Speed)

private fun binder(env: Map<String, String>): ConfigBinder =
  hopliteConfigBinder(report = false, environmentVariables = { env })

val HopliteConfigBinderTest by testSuite {
  test("exported bash functions in the environment are ignored") {
    // what `export -f` / Lmod put in the environment
    val env = mapOf("BASH_FUNC_module%%" to "() {  if [ -z \"\${LMOD_SH_DBG_ON+x}\" ]; then :; fi\n}")

    assert(binder(env).config<ServerConfig>("server") == ServerConfig("localhost", 8080))
  }

  test("shell prompt variables in the environment are ignored") {
    val env = mapOf("PS1" to "\${debian_chroot:+(\$debian_chroot)}\\u@\\h:\\w\\$ ")

    assert(binder(env).config<ServerConfig>("server") == ServerConfig("localhost", 8080))
  }

  test("unresolved substitutions in config still fail") {
    val env = mapOf("SERVER_HOST" to "\${BOOTABLE_TEST_UNSET_VARIABLE}")

    val e = runCatching { binder(env).config<ServerConfig>("server") }.exceptionOrNull()
    assert(e is ConfigException && "Unresolved substitution" in e.message.orEmpty())
  }

  test("enum values match the entries' names in any case") {
    listOf("Fast", "fast", "FAST").forEach {
      assert(binder(mapOf("SPEED_SPEED" to it)).config<SpeedConfig>("speed") == SpeedConfig(Speed.Fast))
    }
  }

  test("environment variables override config with underscores as separators") {
    val env = mapOf("SERVER_PORT" to "9090")

    assert(binder(env).config<ServerConfig>("server") == ServerConfig("localhost", 9090))
  }
}
