package com.github.rocketraman.bootable.config

import com.github.rocketraman.bootable.boot.ServiceOrderConfig
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.serviceOrderConfig
import com.sksamuel.hoplite.ConfigException
import com.sksamuel.hoplite.PropertySource
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

val ServiceOrderConfigTest by testSuite {
  fun hocon(conf: String, strict: Boolean = false) =
    hopliteConfigBinder(report = false, strict = strict, environmentVariables = { emptyMap() }, withConfigStage2 = {
      addPropertySource(PropertySource.string(conf, "conf"))
    })

  test("is all defaults when the section is absent") {
    assert(serviceOrderConfig(binder(emptyMap())) == ServiceOrderConfig())
  }

  test("is read from environment variables, the tiebreaker by its type") {
    val env = mapOf("BOOT_SERVICE_ORDER_TIEBREAKER_TYPE" to "Shuffled", "BOOT_SERVICE_ORDER_TIEBREAKER_SEED" to "42")
    assert(serviceOrderConfig(binder(env)).tiebreaker == StartOrder.Tiebreaker.Shuffled(42))

    val byName = mapOf("BOOT_SERVICE_ORDER_TIEBREAKER_TYPE" to "ByName")
    assert(serviceOrderConfig(binder(byName)).tiebreaker == StartOrder.Tiebreaker.ByName)
  }

  test("shuffles with a new seed when none is given") {
    val env = mapOf("BOOT_SERVICE_ORDER_TIEBREAKER_TYPE" to "Shuffled")
    assert(serviceOrderConfig(binder(env)).tiebreaker is StartOrder.Tiebreaker.Shuffled)
  }

  test("is read from config") {
    val binder = hocon("boot.service.order.tiebreaker { type = Shuffled, seed = 42 }")
    assert(serviceOrderConfig(binder).tiebreaker == StartOrder.Tiebreaker.Shuffled(42))
  }

  test("in strict mode, a seed without a shuffle fails") {
    val binder = hocon("boot.service.order.tiebreaker { type = ByName, seed = 42 }", strict = true)
    val e = runCatching { serviceOrderConfig(binder) }.exceptionOrNull()
    val message = e?.message.orEmpty()
    assert(e is ConfigException && "'boot.service.order.tiebreaker.seed'" in message && "unused" in message)
  }
}
