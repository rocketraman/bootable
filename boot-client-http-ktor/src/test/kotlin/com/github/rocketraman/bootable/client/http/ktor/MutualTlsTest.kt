package com.github.rocketraman.bootable.client.http.ktor

import com.github.rocketraman.bootable.test.runSuspendCatching
import com.github.rocketraman.bootable.config.Masked
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.network.tls.certificates.KeyType
import java.security.KeyStore
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes

private val serverCertificate = TestCertificate("server", KeyType.Server)
private val clientCertificate = TestCertificate("client", KeyType.Client)
private val otherCertificate = TestCertificate("other", KeyType.Client)

private val engines = listOf(
  "CIO" to CioClientEngine(),
  "Java" to JavaClientEngine(),
)

/** Calls [server] once with a client whose TLS settings are [tls], and returns the response's body. */
private suspend fun call(engine: KtorClientEngine, server: MutualTlsServer, tls: KtorClientTlsConfig): String {
  val config = KtorClientConfig(baseUrl = server.url, headers = mapOf("X-Test" to Masked("mtls")), tls = tls)
  val service = KtorClientService(KtorClientsConfig(config), engine)
  try {
    return service.default.get("hello").bodyAsText()
  } finally {
    service.shutdown()
  }
}

private val mutualTls = KtorClientTlsConfig(keyStore = clientCertificate.keyStore().inline(), trustStore = serverCertificate.trustStore().inline())

val MutualTlsTest by testSuite {
  for ((name, engine) in engines) {
    testSuite("on $name") {
      test("presents the client certificate from an inline key store, trusting an inline trust store") {
        MutualTlsServer(serverCertificate, clientCertificate).use { server ->
          assert(call(engine, server, mutualTls) == "CN=client mtls")
        }
      }

      test("fails without a client certificate") {
        MutualTlsServer(serverCertificate, clientCertificate).use { server ->
          val tls = KtorClientTlsConfig(trustStore = serverCertificate.trustStore().inline())
          assert(runSuspendCatching { call(engine, server, tls) }.isFailure)
        }
      }

      test("fails when the server isn't trusted") {
        MutualTlsServer(serverCertificate, clientCertificate).use { server ->
          val tls = KtorClientTlsConfig(keyStore = clientCertificate.keyStore().inline())
          assert(runSuspendCatching { call(engine, server, tls) }.isFailure)
        }
      }
    }
  }

  test("loads the key and trust stores from files") {
    val keyStore = createTempFile("client", ".p12").apply { writeBytes(clientCertificate.keyStore().bytes()) }
    val trustStore = createTempFile("trust", ".p12").apply { writeBytes(serverCertificate.trustStore().bytes()) }
    try {
      MutualTlsServer(serverCertificate, clientCertificate).use { server ->
        val tls = KtorClientTlsConfig(
          keyStore = KeyStoreConfig(path = keyStore.toString(), password = Masked(PASSWORD)),
          trustStore = KeyStoreConfig(path = trustStore.toString(), password = Masked(PASSWORD)),
        )
        assert(call(CioClientEngine(), server, tls) == "CN=client mtls")
      }
    } finally {
      keyStore.deleteIfExists()
      trustStore.deleteIfExists()
    }
  }

  test("trusts every certificate of a PEM trust store, a bundle of several, from a file") {
    // the server's certificate second, so trusting only the first would fail
    val pem = createTempFile("ca", ".crt").apply {
      writeBytes(listOf(otherCertificate, serverCertificate).joinToString("") { it.chain.first().pem() }.toByteArray())
    }
    try {
      MutualTlsServer(serverCertificate, clientCertificate).use { server ->
        val tls = mutualTls.copy(trustStore = KeyStoreConfig(path = pem.toString(), type = "PEM"))
        for ((_, engine) in engines) assert(call(engine, server, tls) == "CN=client mtls")
      }
    } finally {
      pem.deleteIfExists()
    }
  }

  test("a PEM trust store can be given inline, as base64") {
    val base64 = java.util.Base64.getEncoder().encodeToString(serverCertificate.chain.first().pem().toByteArray())
    val tls = ClientTls.load(KtorClientTlsConfig(trustStore = KeyStoreConfig(base64 = Masked(base64), type = "PEM")))!!
    assert(tls.trustManager!!.acceptedIssuers.single().subjectX500Principal.name == "CN=server")
  }

  test("a key store can't be PEM") {
    val failure = runCatching { ClientTls.load(KtorClientTlsConfig(keyStore = KeyStoreConfig(path = "x", type = "pem"))) }
    assert(failure.exceptionOrNull()?.message?.contains("can't be PEM") == true)
  }

  testSuite("a key store with several keys") {
    val both = KeyStore.getInstance("PKCS12").apply {
      load(null, null)
      setKeyEntry("other", otherCertificate.key, PASSWORD.toCharArray(), otherCertificate.chain)
      setKeyEntry("client", clientCertificate.key, PASSWORD.toCharArray(), clientCertificate.chain)
    }

    test("presents the key named by keyAlias") {
      MutualTlsServer(serverCertificate, clientCertificate).use { server ->
        assert(call(JavaClientEngine(), server, mutualTls.copy(keyStore = both.inline(), keyAlias = "client")) == "CN=client mtls")
      }
    }

    test("needs a keyAlias") {
      val failure = runCatching { ClientTls.load(KtorClientTlsConfig(keyStore = both.inline())) }.exceptionOrNull()
      assert(failure?.message?.contains("exactly one key entry") == true)
    }
  }

  test("a key store needs one of base64 and path") {
    val failure = runCatching { ClientTls.load(KtorClientTlsConfig(keyStore = KeyStoreConfig())) }.exceptionOrNull()
    assert(failure?.message?.contains("needs one of base64 and path") == true)
  }

  test("an empty tls block is no TLS material") {
    assert(ClientTls.load(KtorClientTlsConfig()) == null)
  }
}

private fun java.security.cert.Certificate.pem(): String {
  val encoded = java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(this.encoded)
  return "-----BEGIN CERTIFICATE-----\n$encoded\n-----END CERTIFICATE-----\n"
}
