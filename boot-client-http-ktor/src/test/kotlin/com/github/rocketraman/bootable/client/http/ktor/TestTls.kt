package com.github.rocketraman.bootable.client.http.ktor

import com.github.rocketraman.bootable.config.Masked
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsExchange
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import io.ktor.network.tls.certificates.KeyType
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.extensions.HashAlgorithm
import io.ktor.network.tls.extensions.SignatureAlgorithm
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.Base64
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.security.auth.x500.X500Principal

const val PASSWORD = "changeit"

/** A self-signed certificate and its key, generated for a test, as a server's or a client's. */
class TestCertificate(val alias: String, keyType: KeyType) {
  private val generated: KeyStore = buildKeyStore {
    certificate(alias) {
      password = PASSWORD
      subject = X500Principal("CN=$alias")
      this.keyType = keyType
      hash = HashAlgorithm.SHA256
      sign = SignatureAlgorithm.RSA
      keySizeInBits = 2048
      domains = listOf("localhost")
      ipAddresses = listOf(InetAddress.getByName("127.0.0.1"))
    }
  }
  val key = generated.getKey(alias, PASSWORD.toCharArray()) as PrivateKey
  val chain: Array<Certificate> = generated.getCertificateChain(alias)

  /** A PKCS12 key store with the key and certificate, as an application would be given. */
  fun keyStore(): KeyStore = pkcs12().apply { setKeyEntry(alias, key, PASSWORD.toCharArray(), chain) }

  /** A PKCS12 trust store with only the certificate. */
  fun trustStore(): KeyStore = pkcs12().apply { setCertificateEntry(alias, chain.first()) }
}

private fun pkcs12() = KeyStore.getInstance("PKCS12").apply { load(null, null) }

fun KeyStore.bytes(): ByteArray = ByteArrayOutputStream().also { store(it, PASSWORD.toCharArray()) }.toByteArray()

fun KeyStore.inline() = KeyStoreConfig(base64 = Masked(Base64.getEncoder().encodeToString(bytes())), password = Masked(PASSWORD))

/**
 * A local HTTPS server, with the JDK's own TLS, that requires a client certificate signed by, or the same as, the one
 * it trusts, and answers each request with the client certificate's subject and the request's `X-Test` header.
 */
class MutualTlsServer(server: TestCertificate, trustedClient: TestCertificate) : AutoCloseable {
  private val sslContext = SSLContext.getInstance("TLS").apply {
    val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
      .apply { init(server.keyStore(), PASSWORD.toCharArray()) }.keyManagers
    val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
      .apply { init(trustedClient.trustStore()) }.trustManagers
    init(keyManagers, trustManagers, null)
  }

  private val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
    httpsConfigurator = object : HttpsConfigurator(sslContext) {
      override fun configure(params: HttpsParameters) {
        params.setSSLParameters(sslContext.defaultSSLParameters.apply { needClientAuth = true })
      }
    }
    createContext("/") { exchange ->
      val subject = (exchange as HttpsExchange).sslSession.peerPrincipal.name
      val body = "$subject ${exchange.requestHeaders.getFirst("X-Test")}".toByteArray()
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }
    start()
  }

  val url = "https://127.0.0.1:${this.server.address.port}/"

  override fun close() = server.stop(0)
}
