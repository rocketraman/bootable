package com.github.rocketraman.bootable.leader.k8s

import com.github.rocketraman.bootable.client.http.ktor.JavaClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KeyStoreConfig
import com.github.rocketraman.bootable.client.http.ktor.KtorClientConfig
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.client.http.ktor.KtorClientTlsConfig
import com.github.rocketraman.bootable.client.http.ktor.KtorClientsConfig
import de.infix.testBalloon.framework.core.testSuite
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds

/** A pod's service account, as mounted: a namespace, and a CA certificate, a self-signed one of the tests'. */
private val serviceAccount = Path("src/test/resources/service-account")

private fun leaderConfig(
  assumeLeader: Boolean = false,
  serviceAccountDir: String = serviceAccount.toString(),
  httpClient: String = "kubernetes",
) = KubernetesLeaderElectionConfig(
  leaseName = "lease",
  assumeLeader = assumeLeader,
  serviceAccountDir = serviceAccountDir,
  httpClient = httpClient,
)

/** The HTTP clients, as the application's config and the election's defaults make them. */
private fun clients(appConfig: KtorClientsConfig, leader: KubernetesLeaderElectionConfig = leaderConfig()) =
  KtorClientService(appConfig, JavaClientEngine(), defaults = listOf(kubernetesClientDefaults(leader)))

private fun <T> KtorClientService.use(block: (KtorClientService) -> T): T = try {
  block(this)
} finally {
  shutdown()
}

val KubernetesClientDefaultsTest by testSuite {
  test("in a cluster, the kubernetes client trusts the service account's CA, with no config of the application's") {
    clients(KtorClientsConfig()).use { clients ->
      assert("kubernetes" in clients.names)
      val trustStore = clients.config.resolve("kubernetes").tls?.trustStore
      assert(trustStore == KeyStoreConfig(path = serviceAccount.resolve("ca.crt").toString(), type = "PEM"))
    }
  }

  test("the application's own tls for the client replaces the default") {
    val own = KtorClientTlsConfig(trustStore = KeyStoreConfig(path = serviceAccount.resolve("ca.crt").toString(), type = "pem"))
    clients(KtorClientsConfig(named = mapOf("kubernetes" to KtorClientConfig(tls = own)))).use { clients ->
      assert(clients.config.resolve("kubernetes").tls == own)
    }
  }

  test("the application's other settings for the client apply over the default") {
    val config = KtorClientsConfig(named = mapOf("kubernetes" to KtorClientConfig(requestTimeout = 3.seconds)))
    clients(config).use { clients ->
      val resolved = clients.config.resolve("kubernetes")
      assert(resolved.requestTimeout == 3.seconds)
      assert(resolved.tls?.trustStore?.type == "PEM")
    }
  }

  test("the default is for the client the election is configured to use") {
    clients(KtorClientsConfig(), leaderConfig(httpClient = "apiserver")).use { clients ->
      assert(clients.names == setOf("apiserver"))
    }
  }

  test("outside a cluster, or with assumeLeader, there is no default") {
    assert(kubernetesClientDefaults(leaderConfig(serviceAccountDir = "/nonexistent")).clients().isEmpty())
    assert(kubernetesClientDefaults(leaderConfig(assumeLeader = true)).clients().isEmpty())
    clients(KtorClientsConfig(), leaderConfig(assumeLeader = true)).use { clients ->
      assert(clients.names.isEmpty())
    }
  }
}
