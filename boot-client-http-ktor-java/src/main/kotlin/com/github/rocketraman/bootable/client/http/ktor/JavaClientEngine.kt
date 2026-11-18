package com.github.rocketraman.bootable.client.http.ktor

import io.ktor.client.engine.*
import io.ktor.client.engine.java.*

/**
 * Builds each client on Ktor's Java engine, the JDK's own `java.net.http.HttpClient`, with the JDK's TLS, which
 * supports TLS 1.3, and HTTP/2. It needs no dependency beyond Ktor's, and works in a GraalVM native image.
 * [configure] adjusts the engine's own settings, such as its [protocolVersion][JavaHttpConfig.protocolVersion], after
 * the client's TLS material is applied.
 *
 * Unless the client sets a [KtorClientConfig.requestTimeout], a request has no time limit.
 */
class JavaClientEngine(
  private val configure: JavaHttpConfig.(KtorClientSpec) -> Unit = {},
) : KtorClientEngine {
  override fun create(client: KtorClientSpec): HttpClientEngine = Java.create {
    client.tls?.let { tls -> config { sslContext(tls.sslContext) } }
    configure(client)
  }
}
