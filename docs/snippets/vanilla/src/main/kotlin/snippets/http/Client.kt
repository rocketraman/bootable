package snippets.http

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.JavaClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KeyStoreConfig
import com.github.rocketraman.bootable.client.http.ktor.KtorClientConfig
import com.github.rocketraman.bootable.client.http.ktor.KtorClientCustomizer
import com.github.rocketraman.bootable.client.http.ktor.KtorClientDefaults
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.client.http.ktor.KtorClientTlsConfig
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

// tag::use[]
/** Uses the `github` client, whose base URL and headers come from `http.client.named.github`. */
class GitHubRepos(private val github: HttpClient) {
  suspend fun repo(owner: String, name: String): String = github.get("repos/$owner/$name").bodyAsText()
}
// end::use[]

fun clientApp(binder: ConfigBinder): Bootable {
  // tag::wire[]
  val clients = KtorClientService(binder.config("http.client"), CioClientEngine())
  val repos = GitHubRepos(clients["github"])  // a named client; clients.default is the default one

  return Bootable(
    setOf(
      clients,  // closes the clients when the application stops
      // ... and the services that use `repos`
    ),
  )
  // end::wire[]
}

// tag::customizer[]
val userAgent = KtorClientCustomizer { name, config ->
  config.install(UserAgent) { agent = "greeting/1.0" }
  if (name == "github") config.expectSuccess = true  // a non-2xx response throws
}
// end::customizer[]

// tag::java[]
val http11 = JavaClientEngine { protocolVersion = java.net.http.HttpClient.Version.HTTP_1_1 }
// end::java[]

// tag::defaults[]
/** A module's own client, `platform`, which trusts the platform's mounted CA certificate unless configured. */
val platformClient = KtorClientDefaults {
  mapOf(
    "platform" to KtorClientConfig(
      baseUrl = "https://platform.internal/",
      tls = KtorClientTlsConfig(trustStore = KeyStoreConfig(path = "/var/run/platform/ca.crt", type = "PEM")),
    ),
  )
}
// end::defaults[]

fun customizedClients(binder: ConfigBinder) =
  // tag::options[]
  KtorClientService(
    binder.config("http.client"),
    CioClientEngine(),
    userAgent,                          // a customizer
    defaults = listOf(platformClient),  // named clients' default settings
  )
  // end::options[]
