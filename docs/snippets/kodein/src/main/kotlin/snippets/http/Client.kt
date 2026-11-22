package snippets.http

import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KeyStoreConfig
import com.github.rocketraman.bootable.client.http.ktor.KtorClientConfig
import com.github.rocketraman.bootable.client.http.ktor.KtorClientCustomizer
import com.github.rocketraman.bootable.client.http.ktor.KtorClientDefaults
import com.github.rocketraman.bootable.client.http.ktor.KtorClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.client.http.ktor.KtorClientTlsConfig
import com.github.rocketraman.bootable.client.http.ktor.ktorClientModule
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton

/** Uses the `github` client, whose base URL and headers come from `http.client.named.github`. */
class GitHubRepos(private val github: HttpClient) {
  suspend fun repo(owner: String, name: String): String = github.get("repos/$owner/$name").bodyAsText()
}

// tag::wire[]
val clientModule by DI.Module {
  import(ktorClientModule)  // KtorClientService, from http.client, and the default HttpClient
  bindSingleton<KtorClientEngine> { CioClientEngine() }  // the engine
  bindSingleton { GitHubRepos(instance<KtorClientService>()["github"]) }  // a named client
  // ... and the services that use GitHubRepos
}
// end::wire[]

val clientOptionsModule by DI.Module {
  // tag::options[]
  inBindSet<KtorClientCustomizer> {
    add {
      singleton {
        KtorClientCustomizer { _, config -> config.install(UserAgent) { agent = "greeting/1.0" } }
      }
    }
  }
  inBindSet<KtorClientDefaults> {
    add {
      singleton {
        KtorClientDefaults {
          mapOf(
            "platform" to KtorClientConfig(
              baseUrl = "https://platform.internal/",
              tls = KtorClientTlsConfig(trustStore = KeyStoreConfig(path = "/var/run/platform/ca.crt", type = "PEM")),
            ),
          )
        }
      }
    }
  }
  // end::options[]
}
