@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.client.http.ktor

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.koin.bootableModule as koinBootableModule
import com.github.rocketraman.bootable.boot.setOfAppServices
import com.github.rocketraman.bootable.client.http.ktor.koin.ktorClientModule as koinKtorClientModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.config.Masked
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule as koinHopliteConfigModule
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.job
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.inBindSet
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.instance
import org.kodein.di.singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** Answers as a server would: 503 the first time `flaky` is asked for, and each request's URL and headers otherwise. */
private fun mockEngine() = KtorClientEngine {
  var flaky = 1
  MockEngine { request ->
    if (request.url.encodedPath.endsWith("flaky") && flaky-- > 0) return@MockEngine respond("", HttpStatusCode.ServiceUnavailable)
    val headers = request.headers.entries().sortedBy { it.key }.joinToString(",") { (name, values) -> "$name=${values.joinToString()}" }
    respond("${request.url} $headers")
  }
}

private val headerCustomizer = KtorClientCustomizer { name, config ->
  config.defaultRequest { headers.append("X-Customized", name) }
}

/** A module's default for a client the application doesn't configure, and for one it does. */
private val moduleDefaults = KtorClientDefaults {
  mapOf(
    "module" to KtorClientConfig(baseUrl = "https://module.test/"),
    "github" to KtorClientConfig(baseUrl = "https://overridden.test/", socketTimeout = 9.seconds),
  )
}

sealed interface TestScope

@DependencyGraph(
  TestScope::class,
  additionalScopes = [BootScope::class],
  // named rather than contributed: a @ContributesTo in test code trips the power-assert bug KT-83931 on the hint
  // file Metro generates for it
  bindingContainers = [TestClientBindings::class],
)
interface TestGraph : BootableGraph {
  val clients: KtorClientService
  val httpClient: HttpClient
}

@BindingContainer
object TestClientBindings {
  @Provides @SingleIn(TestScope::class) fun engine(): KtorClientEngine = mockEngine()

  @Provides @IntoSet fun customizer(): KtorClientCustomizer = headerCustomizer

  @Provides @IntoSet fun defaults(): KtorClientDefaults = moduleDefaults
}

/** An application's module, composed as an Inara application composes it, with the test's customizer and defaults. */
@DIModule(composed = true)
interface InaraTestModule : ConfigModule, BootableModule, KtorClientModule {
  @KtorClientCustomizers val testCustomizer: KtorClientCustomizer
  @NamedClientDefaults val testDefaults: KtorClientDefaults
}

private val expectedConfig = KtorClientsConfig(
  default = KtorClientConfig(requestTimeout = 7.seconds, headers = mapOf("X-Default" to Masked("default"))),
  named = mapOf(
    "github" to KtorClientConfig(
      baseUrl = "https://api.github.test/v3/",
      headers = mapOf("Accept" to Masked("application/vnd.github+json")),
      retry = KtorClientRetryConfig(maxRetries = 2, initialDelay = 1.milliseconds),
    ),
  ),
)

/** Calls through the wired clients, as the application would, then stops their service as Bootable would. */
private suspend fun checkClients(services: Set<AppService>, clients: KtorClientService, httpClient: HttpClient) {
  assert(httpClient === clients.default)
  val service = services.filterIsInstance<KtorClientService>().single()
  assert(service === clients)
  service.start()

  val github = clients["github"].get("repos/flaky").bodyAsText()
  assert(
    github == "https://api.github.test/v3/repos/flaky " +
      "Accept=application/vnd.github+json,X-Customized=github,X-Default=default"
  )
  assert(httpClient.get("https://other.test/").bodyAsText().contains("X-Customized=default,X-Default=default"))
  // the module's default client, and its default for github, under the application's settings
  assert(clients.names == setOf("github", "module"))
  assert(clients["module"].get("x").bodyAsText().startsWith("https://module.test/x "))
  assert(clients.config.resolve("github").let { it.baseUrl == "https://api.github.test/v3/" && it.socketTimeout == 9.seconds })

  service.shutdown()
  // a closed client's job completes once the last of its requests' work is done; an open one's never does
  for (client in listOf(clients.default, clients["github"])) {
    client.coroutineContext.job.join()
  }
}

/** Config comes from src/test/resources/application.conf, as it would in an application. */
val KtorClientWiringTest by testSuite {
  testSuite("clients config decoded by") {
    test("hoplite") {
      assert(hopliteConfigBinder(report = false, strict = true).config<KtorClientsConfig>("http.client") == expectedConfig)
    }

    test("hoplite, as all defaults when the section is absent") {
      assert(hopliteConfigBinder(report = false).config<KtorClientsConfig>("absent.client") == KtorClientsConfig())
    }

    test("kodein") {
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(ktorClientModule)
        bindSingleton<KtorClientEngine> { mockEngine() }
        inBindSet<KtorClientCustomizer> { add { singleton { headerCustomizer } } }
        inBindSet<KtorClientDefaults> { add { singleton { moduleDefaults } } }
      }.direct

      checkClients(di.instance(), di.instance(), di.instance())
    }

    test("koin") {
      val koin = koinApplication {
        modules(
          koinBootableModule(),
          koinHopliteConfigModule(),
          koinKtorClientModule(),
          module {
            single<KtorClientEngine> { mockEngine() }
            single<KtorClientCustomizer>(named("test")) { headerCustomizer }
            single<KtorClientDefaults>(named("test")) { moduleDefaults }
          },
        )
      }.koin

      try {
        checkClients(koin.getAll<AppService>().toSet(), koin.get(), koin.get())
      } finally {
        koin.close()
      }
    }

    test("metro") {
      val graph = createGraph<TestGraph>()
      checkClients(graph.appServices, graph.clients, graph.httpClient)
    }

    test("inara") {
      val module = InaraTestModule(
        configModule = hopliteConfigModule(),
        bootableModule = bootableModule(),
        ktorClientModule = ktorClientModule(mockEngine()),
        testCustomizer = Binding.value(headerCustomizer),
        testDefaults = Binding.value(moduleDefaults),
      )
      checkClients(module.setOfAppServices(), module.ktorClientService, module.httpClient)
    }
  }
}
