package snippets.http

import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KeyStoreConfig
import com.github.rocketraman.bootable.client.http.ktor.KtorClientConfig
import com.github.rocketraman.bootable.client.http.ktor.KtorClientCustomizer
import com.github.rocketraman.bootable.client.http.ktor.KtorClientDefaults
import com.github.rocketraman.bootable.client.http.ktor.KtorClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.client.http.ktor.KtorClientTlsConfig
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

/** Uses the `github` client, whose base URL and headers come from `http.client.named.github`. */
class GitHubRepos(private val github: HttpClient) {
  suspend fun repo(owner: String, name: String): String = github.get("repos/$owner/$name").bodyAsText()
}

// tag::wire[]
// KtorClientService and the default HttpClient are contributed by boot-client-http-ktor-metro
@ContributesTo(HttpScope::class)
interface ClientBindings {
  @Provides fun ktorClientEngine(): KtorClientEngine = CioClientEngine()  // the engine

  @Provides @SingleIn(HttpScope::class)
  fun gitHubRepos(clients: KtorClientService): GitHubRepos = GitHubRepos(clients["github"])  // a named client
  // ... and the services that use GitHubRepos
}
// end::wire[]

@ContributesTo(HttpScope::class)
interface ClientOptionsBindings {
  // tag::options[]
  @Provides @IntoSet
  fun userAgent(): KtorClientCustomizer =
    KtorClientCustomizer { _, config -> config.install(UserAgent) { agent = "greeting/1.0" } }

  @Provides @IntoSet
  fun platformClient(): KtorClientDefaults = KtorClientDefaults {
    mapOf(
      "platform" to KtorClientConfig(
        baseUrl = "https://platform.internal/",
        tls = KtorClientTlsConfig(trustStore = KeyStoreConfig(path = "/var/run/platform/ca.crt", type = "PEM")),
      ),
    )
  }
  // end::options[]
}
