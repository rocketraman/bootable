package com.github.rocketraman.bootable.config

import com.github.rocketraman.bootable.boot.ServiceOrderConfig
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.serviceOrderConfig
import com.sksamuel.hoplite.ConfigException
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.secrets.SecretsPolicy
import com.sksamuel.hoplite.secrets.StandardSecretsPolicy
import de.infix.testBalloon.framework.core.testSuite
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.reflect.KType

// public: hoplite constructs it reflectively
data class ServerConfig(val host: String, val port: Int)

enum class Speed { Fast, Slow }

// public: hoplite constructs it reflectively
data class SpeedConfig(val speed: Speed)

// public: hoplite constructs it reflectively
data class PropertiesConfig(val values: Map<String, String>)

// public: hoplite constructs it reflectively
data class DefaultedConfig(val host: String = "default.example.com")

private fun binder(env: Map<String, String>): ConfigBinder =
  hopliteConfigBinder(report = false, environmentVariables = { env })

// public: hoplite constructs it reflectively
data class ApiConfig(val url: String, val apiKey: Masked, val port: Masked, val headers: Map<String, Masked>)

/** What the config report prints while [block] runs. */
private fun report(block: () -> Unit): String {
  val out = System.out
  val captured = ByteArrayOutputStream()
  System.setOut(PrintStream(captured, true))
  try {
    block()
  } finally {
    System.setOut(out)
  }
  return captured.toString()
}

private val apiConf = "api { url = \"https://api.example\", apiKey = key-123, port = 8443, headers { x-tenant = tenant-789, tenant = plain-321 } }"

private fun apiBinder(secretsPolicy: SecretsPolicy = StandardSecretsPolicy) = hopliteConfigBinder(
  withConfigStage3 = { addPropertySource(PropertySource.string(apiConf, "conf")) },
  environmentVariables = { emptyMap() },
  secretsPolicy = secretsPolicy,
)

private object NothingSecret : SecretsPolicy {
  override fun isSecret(node: Node, type: KType?) = false
}

private fun hocon(conf: String, env: Map<String, String> = emptyMap()): ConfigBinder =
  hopliteConfigBinder(report = false, environmentVariables = { env }, withConfigStage2 = {
    addPropertySource(PropertySource.string(conf, "conf"))
  })

val HopliteConfigBinderTest by testSuite {
  test("decodes Masked values in strict mode, maps' included") {
    val binder = hopliteConfigBinder(
      report = false,
      strict = true,
      withConfigStage3 = { addPropertySource(PropertySource.string(apiConf, "conf")) },
      environmentVariables = { emptyMap() },
    )
    assert(binder.config<ApiConfig>("api").headers.getValue("x-tenant") == Masked("tenant-789"))
  }

  test("decodes Masked values, from strings and numbers, and in maps") {
    val config = apiBinder().config<ApiConfig>("api")
    assert(config == ApiConfig("https://api.example", Masked("key-123"), Masked("8443"), mapOf("x-tenant" to Masked("tenant-789"), "tenant" to Masked("plain-321"))))
    assert("key-123" !in config.toString() && "tenant-789" !in config.toString())
  }

  test("masks Masked values in the config report, and shows the rest") {
    val printed = report { apiBinder().config<ApiConfig>("api") }
    assert("https://api.example" in printed)
    assert(listOf("key-123", "8443", "tenant-789", "plain-321").none { it in printed })
    assert("Unused keys: none" in printed)
  }

  test("masks Masked values in the config report whatever the secrets policy") {
    val printed = report { apiBinder(secretsPolicy = NothingSecret).config<ApiConfig>("api") }
    assert("https://api.example" in printed && "key-123" !in printed && "tenant-789" !in printed)
  }

  test("shell code in exported environment variables doesn't fail binding") {
    // https://github.com/sksamuel/hoplite/issues/602: Debian's default prompt, and what `export -f` / Lmod put in
    // the environment
    val env = mapOf(
      "PS1" to "\${debian_chroot:+(\$debian_chroot)}\\u@\\h:\\w\\$ ",
      "BASH_FUNC_ml%%" to "() {  if [ -z \"\${LMOD_SH_DBG_ON+x}\" ]; then :; fi\n}",
    )

    assert(binder(env).config<ServerConfig>("server") == ServerConfig("localhost", 8080))
  }

  test("an env resolver in a string field resolves from the environment variables") {
    val binder = hocon("server.host = \"\${{ env:BOOTABLE_TEST_HOST }}\"", mapOf("BOOTABLE_TEST_HOST" to "example.com"))

    assert(binder.config<ServerConfig>("server") == ServerConfig("example.com", 8080))
  }

  test("an unset env resolver fails") {
    val binder = hocon("server.host = \"\${{ env:BOOTABLE_TEST_HOST }}\"")

    val e = runCatching { binder.config<ServerConfig>("server") }.exceptionOrNull()
    assert(e is ConfigException && "Could not resolve 'BOOTABLE_TEST_HOST'" in e.message.orEmpty())
  }

  test("an unset env resolver fails even in a field with a default") {
    val binder = hocon("defaulted.host = \"\${{ env:BOOTABLE_TEST_HOST }}\"")

    val e = runCatching { binder.config<DefaultedConfig>("defaulted") }.exceptionOrNull()
    assert(e is ConfigException && "Could not resolve 'BOOTABLE_TEST_HOST'" in e.message.orEmpty())
  }

  test("an unset env resolver with a fallback is the fallback") {
    val binder = hocon("server.host = \"\${{ env:BOOTABLE_TEST_HOST:-fallback.example.com }}\"")

    assert(binder.config<ServerConfig>("server") == ServerConfig("fallback.example.com", 8080))
  }

  test("a ref resolver resolves another config value, and an env resolver resolves into a non-string field") {
    val binder = hocon(
      "app.host = example.com\nserver.host = \"\${{ ref:app.host }}\"\nserver.port = \"\${{ env:BOOTABLE_TEST_PORT }}\"",
      mapOf("BOOTABLE_TEST_PORT" to "9090"),
    )

    assert(binder.config<ServerConfig>("server") == ServerConfig("example.com", 9090))
  }

  test("an env resolver resolves into a Masked field") {
    val binder = hopliteConfigBinder(
      report = false,
      withConfigStage3 = {
        addPropertySource(PropertySource.string(apiConf.replace("key-123", "\"\${{ env:BOOTABLE_TEST_KEY }}\""), "conf"))
      },
      environmentVariables = { mapOf("BOOTABLE_TEST_KEY" to "key-456") },
    )

    assert(binder.config<ApiConfig>("api").apiKey == Masked("key-456"))
  }

  test("preprocessor substitutions are left as literal text") {
    val binder = hocon("server.host = \"\${BOOTABLE_TEST_HOST}\"", mapOf("BOOTABLE_TEST_HOST" to "example.com"))

    assert(binder.config<ServerConfig>("server") == ServerConfig("\${BOOTABLE_TEST_HOST}", 8080))
  }

  // HOCON resolves its own substitutions at parse time, falling back to the process environment, which the
  // environmentVariables parameter doesn't reach; so these reference PATH, which is set, and a variable that isn't.
  test("HOCON substitutions resolve, in strings and in maps") {
    val path = System.getenv("PATH")
    val binder = hocon(
      """
      base = "example.com"
      server.host = ${'$'}{base}
      server.host = ${'$'}{?BOOTABLE_TEST_UNSET_VARIABLE}
      properties.values {
        path = ${'$'}{PATH}
        optionalPath = ${'$'}{?PATH}
        base = ${'$'}{base}
      }
      """.trimIndent()
    )

    assert(binder.config<ServerConfig>("server") == ServerConfig("example.com", 8080))
    assert(
      binder.config<PropertiesConfig>("properties").values ==
        mapOf("path" to path, "optionalPath" to path, "base" to "example.com")
    )
  }

  test("an env resolver in a map value is not resolved") {
    // Hoplite's resolvers only resolve a data class field whose value is a string, not the values of a map, list,
    // or set; see ContextResolver.resolve in hoplite-core. If this fails, Hoplite resolves them now: update this
    // test, the README, and the CHANGELOG.
    val binder = hocon(
      "properties.values.host = \"\${{ env:BOOTABLE_TEST_HOST }}\"",
      mapOf("BOOTABLE_TEST_HOST" to "example.com"),
    )

    assert(binder.config<PropertiesConfig>("properties").values == mapOf("host" to "\${{ env:BOOTABLE_TEST_HOST }}"))
  }

  test("enum values match the entries' names in any case") {
    listOf("Fast", "fast", "FAST").forEach {
      assert(binder(mapOf("SPEED_SPEED" to it)).config<SpeedConfig>("speed") == SpeedConfig(Speed.Fast))
    }
  }

  test("an environment variable overrides a map entry without respelling its key") {
    val binder = hocon("properties.values { alphaKey = a }", mapOf("PROPERTIES_VALUES_ALPHAKEY" to "b"))

    assert(binder.config<PropertiesConfig>("properties").values == mapOf("alphaKey" to "b"))
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

  test("is read from environment variables, the tiebreaker by its own value") {
    val env = mapOf("BOOT_SERVICE_ORDER_TIEBREAKER" to "Shuffled", "BOOT_SERVICE_ORDER_TIEBREAKER_SEED" to "42")
    assert(serviceOrderConfig(binder(env)).tiebreaker == StartOrder.Tiebreaker.Shuffled(42))

    val byName = mapOf("BOOT_SERVICE_ORDER_TIEBREAKER" to "ByName")
    assert(serviceOrderConfig(binder(byName)).tiebreaker == StartOrder.Tiebreaker.ByName)
  }

  test("shuffles with a new seed when none is given") {
    val env = mapOf("BOOT_SERVICE_ORDER_TIEBREAKER" to "Shuffled")
    assert(serviceOrderConfig(binder(env)).tiebreaker is StartOrder.Tiebreaker.Shuffled)
  }

  test("is read from config") {
    val binder = hocon("boot.service.order.tiebreaker { _type = Shuffled, seed = 42 }")
    assert(serviceOrderConfig(binder).tiebreaker == StartOrder.Tiebreaker.Shuffled(42))
  }

  test("the tiebreaker's subtype name matches in any case") {
    listOf("shuffled", "SHUFFLED").forEach {
      val env = mapOf("BOOT_SERVICE_ORDER_TIEBREAKER" to it, "BOOT_SERVICE_ORDER_TIEBREAKER_SEED" to "42")
      assert(serviceOrderConfig(binder(env)).tiebreaker == StartOrder.Tiebreaker.Shuffled(42))
    }
  }

  test("an environment variable selects the tiebreaker over config") {
    val conf = "boot.service.order.tiebreaker { _type = ByName, seed = 42 }"
    val binder = hopliteConfigBinder(
      report = false,
      environmentVariables = { mapOf("BOOT_SERVICE_ORDER_TIEBREAKER" to "Shuffled") },
      withConfigStage2 = { addPropertySource(PropertySource.string(conf, "conf")) },
    )
    assert(serviceOrderConfig(binder).tiebreaker == StartOrder.Tiebreaker.Shuffled(42))
  }

  test("in strict mode, a seed without a shuffle fails") {
    val binder = hocon("boot.service.order.tiebreaker { _type = ByName, seed = 42 }", strict = true)
    val e = runCatching { serviceOrderConfig(binder) }.exceptionOrNull()
    val message = e?.message.orEmpty()
    assert(e is ConfigException && "'boot.service.order.tiebreaker.seed'" in message && "unused" in message)
  }
}
