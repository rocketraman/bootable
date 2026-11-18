package com.github.rocketraman.bootable.client.http.ktor

import io.ktor.client.engine.*

/**
 * What a [KtorClientService] needs to build a client: its [name], its [config], with what it leaves unset taken from
 * the default client's, and its [tls] material, loaded from [KtorClientConfig.tls], or null for none.
 */
class KtorClientSpec(
  val name: String,
  val config: KtorClientConfig,
  val tls: ClientTls?,
)

/**
 * Creates the engine of each of a [KtorClientService]'s clients. The engine modules provide one each:
 * `CioClientEngine` (boot-client-http-ktor-cio), and `JavaClientEngine` (boot-client-http-ktor-java), each applying
 * the client's TLS material in its own terms. For another engine, write one: create the engine from the
 * [KtorClientSpec], applying its [ClientTls.sslContext].
 *
 * It is also the seam for tests: one that returns Ktor's `MockEngine` answers every client's requests without a
 * server, with no other change to the wiring:
 *
 * ```
 * KtorClientEngine { MockEngine { request -> respond("hello") } }
 * ```
 *
 * The service closes each engine it creates, after its client.
 */
fun interface KtorClientEngine {
  fun create(client: KtorClientSpec): HttpClientEngine
}
