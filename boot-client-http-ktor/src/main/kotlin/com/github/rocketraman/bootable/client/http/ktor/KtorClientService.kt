package com.github.rocketraman.bootable.client.http.ktor

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Milestone
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.boot.milestone
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import org.apache.logging.log4j.kotlin.logger

/** The HTTP clients' milestones, so a service in the same phase can order itself against them. */
object HttpClientMilestones {
  /** The HTTP clients are ready to use. Provided by [KtorClientService]. */
  val ClientsReady: Milestone = milestone("http.clients.ready")
}

/**
 * Owns the application's Ktor [HttpClient]s, and closes them, and their engines, when the application stops.
 *
 * There is a [default] client, and one for each of [KtorClientsConfig.named], by name: see [get] and [getOrDefault].
 * Each is Ktor's own [HttpClient], built on an engine from [engine], with the typed settings of its [KtorClientConfig]
 * applied through Ktor's own plugins: `HttpTimeout` for the timeouts, `DefaultRequest` for the base URL, and
 * `HttpRequestRetry` for retries, and a plugin of its own for the default headers. Then [customizer] adds whatever
 * else the application wants, such as `ContentNegotiation`. Modules can contribute named clients' default settings
 * with [defaults], which the application's own settings in [appConfig] override: see [KtorClientDefaults].
 *
 * ```
 * val clients = KtorClientService(binder.config("http.client"), CioClientEngine())
 * val github = clients["github"]
 * ```
 *
 * The clients are built when this is constructed, so they can be handed to other components right away, and a bad
 * setting, such as an unreadable key store, fails the application at boot.
 *
 * The service is in the [DefaultPhase.Infrastructure] phase, so services that make calls, in later phases, stop
 * before the clients close, and an in-flight call isn't cut off. It provides [HttpClientMilestones.ClientsReady], for
 * anything in the same phase that must start after the clients and stop before them.
 */
class KtorClientService(
  appConfig: KtorClientsConfig,
  engine: KtorClientEngine,
  customizer: KtorClientCustomizer = KtorClientCustomizer.None,
  defaults: Iterable<KtorClientDefaults> = emptyList(),
) : AppService {

  private val engines = mutableListOf<HttpClientEngine>()
  private val clients = linkedMapOf<String, HttpClient>()

  /** The clients' settings: the application's, with the named clients' [KtorClientDefaults] added. */
  val config: KtorClientsConfig = appConfig.withContributed(contributed(defaults))

  init {
    require(KtorClientsConfig.DEFAULT !in config.named) {
      "A named HTTP client can't be called ${KtorClientsConfig.DEFAULT}; configure the default client as http.client.default"
    }
    try {
      (listOf(KtorClientsConfig.DEFAULT) + config.named.keys).forEach { name ->
        val resolved = config.resolve(name)
        clients[name] = build(KtorClientSpec(name, resolved, ClientTls.load(resolved.tls)), engine, customizer)
      }
    } catch (e: Throwable) {
      close()
      throw e
    }
  }

  /** The default client. */
  val default: HttpClient get() = clients.getValue(KtorClientsConfig.DEFAULT)

  /** The names of the named clients. */
  val names: Set<String> get() = clients.keys - KtorClientsConfig.DEFAULT

  /** The client named [name]. Fails if there is no such client: see [getOrDefault] for one that may not be. */
  operator fun get(name: String): HttpClient =
    clients[name] ?: throw NoSuchElementException("No HTTP client named $name; the named clients are $names")

  /** The client named [name], if there is one, or else the [default] client. */
  fun getOrDefault(name: String): HttpClient = clients[name] ?: default

  override fun name() = "KtorClientService"

  override suspend fun shutdown() = close()

  override fun ordering() = Ordering(DefaultPhase.Infrastructure, provides = setOf(HttpClientMilestones.ClientsReady))

  private fun build(spec: KtorClientSpec, engineFactory: KtorClientEngine, customizer: KtorClientCustomizer): HttpClient {
    val engine = engineFactory.create(spec).also(engines::add)
    val config = spec.config
    return HttpClient(engine) {
      if (config.connectTimeout != null || config.requestTimeout != null || config.socketTimeout != null) {
        install(HttpTimeout) {
          config.connectTimeout?.let { connectTimeoutMillis = it.inWholeMilliseconds }
          config.requestTimeout?.let { requestTimeoutMillis = it.inWholeMilliseconds }
          config.socketTimeout?.let { socketTimeoutMillis = it.inWholeMilliseconds }
        }
      }
      config.baseUrl?.let { baseUrl -> defaultRequest { url(baseUrl) } }
      if (config.headers.isNotEmpty()) install(defaultHeaders(config.headers.mapValues { it.value.value }))
      config.retry?.takeIf { it.maxRetries > 0 }?.let { retry -> install(HttpRequestRetry) { retry(retry) } }
      customizer.customize(spec.name, this)
    }
  }

  /** Closes every client, then every engine, logging rather than throwing a failure, so the rest are still closed. */
  private fun close() {
    (clients.values + engines).forEach { closeable ->
      try {
        closeable.close()
      } catch (e: Exception) {
        log.warn(e) { "Failed to close $closeable" }
      }
    }
  }

  companion object {
    private val log = logger()

    /** Every contributed default, by client name, failing if two contributors default the same client. */
    private fun contributed(defaults: Iterable<KtorClientDefaults>): Map<String, KtorClientConfig> {
      val byName = mutableMapOf<String, Pair<KtorClientDefaults, KtorClientConfig>>()
      for (contributor in defaults) {
        for ((name, config) in contributor.clients()) {
          val earlier = byName.put(name, contributor to config)
          require(earlier == null) {
            "Two KtorClientDefaults default the HTTP client $name: ${earlier!!.first.javaClass.name} and " +
              contributor.javaClass.name
          }
        }
      }
      return byName.mapValues { it.value.second }
    }

    /**
     * Adds [headers] to each request that doesn't set them itself. Unlike `DefaultRequest`'s headers, which would add
     * a second value, a request's own header replaces the default.
     */
    private fun defaultHeaders(headers: Map<String, String>) = createClientPlugin("BootableDefaultHeaders") {
      onRequest { request, _ ->
        headers.forEach { (name, value) -> if (!request.headers.contains(name)) request.headers.append(name, value) }
      }
    }
  }
}
