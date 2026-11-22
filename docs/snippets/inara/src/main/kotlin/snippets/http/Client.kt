package snippets.http

import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KeyStoreConfig
import com.github.rocketraman.bootable.client.http.ktor.KtorClientConfig
import com.github.rocketraman.bootable.client.http.ktor.KtorClientCustomizer
import com.github.rocketraman.bootable.client.http.ktor.KtorClientCustomizers
import com.github.rocketraman.bootable.client.http.ktor.KtorClientDefaults
import com.github.rocketraman.bootable.client.http.ktor.KtorClientModule
import com.github.rocketraman.bootable.client.http.ktor.KtorClientTlsConfig
import com.github.rocketraman.bootable.client.http.ktor.NamedClientDefaults
import com.github.rocketraman.bootable.client.http.ktor.ktorClientModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/** Uses the `github` client, whose base URL and headers come from `http.client.named.github`. */
class GitHubRepos(private val github: HttpClient) {
  suspend fun repo(owner: String, name: String): String = github.get("repos/$owner/$name").bodyAsText()
}

// tag::wire[]
@DIModule
interface ClientModule : @DIModule.Required KtorClientModule {
  val gitHubRepos: GitHubRepos
  // ... and the services that use GitHubRepos
}

fun clientModule() = ClientModulePart(
  gitHubRepos = Binding.single { GitHubRepos(ktorClientService["github"]) },  // a named client
)

@DIModule(composed = true)
interface ClientAppModule : ConfigModule, BootableModule, KtorClientModule, ClientModule

fun clientAppModule() = ClientAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  ktorClientModule = ktorClientModule(CioClientEngine()),  // ktorClientService, from http.client, on that engine
  clientModule = clientModule(),
)
// end::wire[]

// tag::options[]
@DIModule
interface ClientOptionsModule : @DIModule.Required KtorClientModule {
  @KtorClientCustomizers val userAgent: KtorClientCustomizer
  @NamedClientDefaults val platformClient: KtorClientDefaults
}

fun clientOptionsModule() = ClientOptionsModulePart(
  userAgent = Binding.single {
    KtorClientCustomizer { _, config -> config.install(UserAgent) { agent = "greeting/1.0" } }
  },
  platformClient = Binding.single {
    KtorClientDefaults {
      mapOf(
        "platform" to KtorClientConfig(
          baseUrl = "https://platform.internal/",
          tls = KtorClientTlsConfig(trustStore = KeyStoreConfig(path = "/var/run/platform/ca.crt", type = "PEM")),
        ),
      )
    }
  },
)
// end::options[]
