package com.github.rocketraman.bootable.client.http.ktor

import java.io.ByteArrayInputStream
import java.io.File
import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * A client's TLS material, loaded from its [KtorClientTlsConfig], in the forms the engines take it: the JDK-based
 * engines take an [sslContext], and CIO, which has its own TLS, the [certificateChain], [privateKey] and
 * [trustManager].
 */
class ClientTls(
  /** The client certificate's chain, to present for mutual TLS, or null to present none. */
  val certificateChain: List<X509Certificate>?,
  /** The client certificate's private key, or null to present none. */
  val privateKey: PrivateKey?,
  /** The trust manager for the configured trust store, or null to trust the platform's certificate authorities. */
  val trustManager: X509TrustManager?,
) {
  /** Whether there is a client certificate to present. */
  val hasClientCertificate: Boolean get() = certificateChain != null && privateKey != null

  /**
   * An [SSLContext] that presents the client certificate, if there is one, and trusts the [trustManager], if there is
   * one. The certificate is presented whatever certificate authorities the server asks for, as CIO presents it, since
   * a self-signed client certificate's issuer is never among them; the server decides whether to accept it.
   */
  val sslContext: SSLContext by lazy {
    val keyManager = if (hasClientCertificate) FixedKeyManager(certificateChain!!.toTypedArray(), privateKey!!) else null
    SSLContext.getInstance("TLS").apply {
      init(keyManager?.let { arrayOf(it) }, trustManager?.let { arrayOf(it) }, null)
    }
  }

  companion object {
    /** Loads the material [config] names, or returns null if it names none. Fails on a missing or unreadable store. */
    fun load(config: KtorClientTlsConfig?): ClientTls? {
      if (config == null || (config.keyStore == null && config.trustStore == null)) return null

      var chain: List<X509Certificate>? = null
      var key: PrivateKey? = null
      config.keyStore?.let { storeConfig ->
        val password = storeConfig.password?.value?.toCharArray()
        require(!storeConfig.type.equals(PEM, ignoreCase = true)) {
          "tls.keyStore can't be PEM: give the key and its certificate chain as a PKCS12 or JKS key store"
        }
        val store = storeConfig.load("tls.keyStore", password)
        val alias = config.keyAlias ?: store.aliases().toList().filter(store::isKeyEntry).let { keys ->
          require(keys.size == 1) {
            "tls.keyStore must hold exactly one key entry when tls.keyAlias is unset, but holds ${keys.size}"
          }
          keys.single()
        }
        require(store.isKeyEntry(alias)) { "tls.keyStore has no key entry $alias" }
        key = store.getKey(alias, config.keyPassword?.value?.toCharArray() ?: password) as PrivateKey
        chain = store.getCertificateChain(alias).map { it as X509Certificate }
      }

      val trustManager = config.trustStore?.let { storeConfig ->
        val store = storeConfig.load("tls.trustStore", storeConfig.password?.value?.toCharArray())
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
          .apply { init(store) }
          .trustManagers
          .filterIsInstance<X509TrustManager>()
          .first()
      }

      return ClientTls(chain, key, trustManager)
    }

    private const val PEM = "PEM"

    private fun KeyStoreConfig.load(what: String, password: CharArray?): KeyStore {
      require((base64 == null) != (path == null)) { "$what needs one of base64 and path" }
      val bytes = base64?.let {
        try {
          Base64.getMimeDecoder().decode(it.value.trim())
        } catch (e: IllegalArgumentException) {
          throw IllegalArgumentException("$what.base64 is not valid base64", e)
        }
      } ?: File(path!!).readBytes()
      if (type.equals(PEM, ignoreCase = true)) {
        val certificates = ByteArrayInputStream(bytes).use { CertificateFactory.getInstance("X.509").generateCertificates(it) }
        require(certificates.isNotEmpty()) { "$what holds no PEM certificates" }
        return KeyStore.getInstance(KeyStore.getDefaultType()).apply {
          load(null, null)
          certificates.forEachIndexed { i, certificate -> setCertificateEntry("certificate-$i", certificate) }
        }
      }
      return KeyStore.getInstance(type).apply { ByteArrayInputStream(bytes).use { load(it, password) } }
    }
  }
}

/** Presents [chain] as the client certificate, whatever the server asks for, and is never a server's key manager. */
private class FixedKeyManager(
  private val chain: Array<X509Certificate>,
  private val key: PrivateKey,
) : X509ExtendedKeyManager() {
  override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
  override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) =
    ALIAS
  override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
  override fun getCertificateChain(alias: String?) = if (alias == ALIAS) chain else null
  override fun getPrivateKey(alias: String?) = if (alias == ALIAS) key else null
  override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
  override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null

  companion object {
    const val ALIAS = "client"
  }
}
