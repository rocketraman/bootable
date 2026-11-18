package com.github.rocketraman.bootable.leader.k8s

import com.github.rocketraman.bootable.client.http.ktor.KeyStoreConfig
import com.github.rocketraman.bootable.client.http.ktor.KtorClientConfig
import com.github.rocketraman.bootable.client.http.ktor.KtorClientDefaults
import com.github.rocketraman.bootable.client.http.ktor.KtorClientTlsConfig
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * The default config of the HTTP client [KubernetesLeaderElection] calls the API server with, the one named by
 * [KubernetesLeaderElectionConfig.httpClient]: a trust store of the pod's service account CA certificate, `ca.crt`,
 * so that, in a cluster, the client trusts the API server with no TLS config of the application's.
 *
 * The application's own config of that client, in `http.client.named`, overrides it: its `tls`, if it sets one,
 * replaces this whole. Without the certificate, as outside a cluster, or with
 * [KubernetesLeaderElectionConfig.assumeLeader], which makes no calls, it defaults nothing.
 *
 * The DI flavors contribute it; with vanilla wiring, give it to `KtorClientService`:
 *
 * ```
 * KtorClientService(binder.config("http.client"), CioClientEngine(), defaults = listOf(kubernetesClientDefaults(config)))
 * ```
 */
fun kubernetesClientDefaults(config: KubernetesLeaderElectionConfig): KtorClientDefaults = KtorClientDefaults {
  val caCert = Path(config.serviceAccountDir).resolve("ca.crt")
  if (config.assumeLeader || !caCert.exists()) {
    emptyMap()
  } else {
    val trustStore = KeyStoreConfig(path = caCert.toString(), type = "PEM")
    mapOf(config.httpClient to KtorClientConfig(tls = KtorClientTlsConfig(trustStore = trustStore)))
  }
}
