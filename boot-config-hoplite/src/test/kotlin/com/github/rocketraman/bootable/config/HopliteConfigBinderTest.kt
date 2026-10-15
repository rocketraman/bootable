package com.github.rocketraman.bootable.config

import com.sksamuel.hoplite.ConfigException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HopliteConfigBinderTest {
  data class ServerConfig(val host: String, val port: Int)

  private fun binder(env: Map<String, String>): ConfigBinder =
    hopliteConfigBinder(report = false, environmentVariables = { env })

  @Test
  fun `unresolved substitutions in config still fail`() {
    val env = mapOf("SERVER_HOST" to "\${BOOTABLE_TEST_UNSET_VARIABLE}")

    val e = assertFailsWith<ConfigException> { binder(env).config<ServerConfig>("server") }
    assertContains(e.message.orEmpty(), "Unresolved substitution")
  }

  @Test
  fun `environment variables override config with underscores as separators`() {
    val env = mapOf("SERVER_PORT" to "9090")

    assertEquals(ServerConfig("localhost", 9090), binder(env).config<ServerConfig>("server"))
  }
}
