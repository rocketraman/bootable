package com.github.rocketraman.bootable.client.http.ktor

import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.network.tls.*

/**
 * Builds each client on Ktor's CIO engine: pure Kotlin, on coroutines, and the engine that works best in a GraalVM
 * native image. [configure] adjusts the engine's own settings, such as its connection limits, after the client's TLS
 * material and request timeout are applied.
 *
 * CIO has its own TLS implementation, which supports TLS 1.2 but not 1.3 (KTOR-6737): for a server that only accepts
 * TLS 1.3, use `JavaClientEngine`, which uses the JDK's TLS.
 *
 * Unless the client sets a [KtorClientConfig.requestTimeout], CIO's own default of 15 seconds applies.
 */
class CioClientEngine(
  private val configure: CIOEngineConfig.(KtorClientSpec) -> Unit = {},
) : KtorClientEngine {
  override fun create(client: KtorClientSpec): HttpClientEngine = CIO.create {
    client.tls?.let { tls ->
      https {
        tls.trustManager?.let { trustManager = it }
        if (tls.hasClientCertificate) addCertificateChain(tls.certificateChain!!.toTypedArray(), tls.privateKey!!)
      }
    }
    // CIO applies its own request timeout as well as HttpTimeout's, so a longer one is cut short without this
    client.config.requestTimeout?.let { requestTimeout = it.inWholeMilliseconds }
    configure(client)
  }
}
