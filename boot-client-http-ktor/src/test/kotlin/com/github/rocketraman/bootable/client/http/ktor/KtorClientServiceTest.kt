package com.github.rocketraman.bootable.client.http.ktor

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.startPlan
import com.github.rocketraman.bootable.config.Masked
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** A [KtorClientEngine] whose engines answer every request with [handler], recording each request it is asked. */
private class MockClientEngine(private val handler: MockRequestHandler = { respondOk("ok") }) : KtorClientEngine {
  val requests = CopyOnWriteArrayList<HttpRequestData>()
  val engines = CopyOnWriteArrayList<MockEngine>()
  val specs = CopyOnWriteArrayList<KtorClientSpec>()

  override fun create(client: KtorClientSpec) = MockEngine { request ->
    requests += request
    handler(request)
  }.also { engines += it; specs += client }
}

private val noDelay = KtorClientRetryConfig(initialDelay = 1.milliseconds, maxDelay = 1.milliseconds, jitter = 0.milliseconds)

/** Answers with each of [statuses] in turn, then with 200. */
private fun statuses(vararg statuses: HttpStatusCode): MockRequestHandler {
  val remaining = ArrayDeque(statuses.toList())
  return { respond("", remaining.removeFirstOrNull() ?: HttpStatusCode.OK) }
}

val KtorClientServiceTest by testSuite {
  testSuite("lifecycle") {
    test("the clients and their engines are closed on shutdown") {
      val engine = MockClientEngine()
      val service = KtorClientService(KtorClientsConfig(named = mapOf("a" to KtorClientConfig())), engine)
      service.start()
      assert(service.default.get("/").bodyAsText() == "ok")
      assert(service.default.isActive && service["a"].isActive)
      service.shutdown()
      // a closed client's job, and engine's, completes once the last of its work is done; an open one's never does
      (listOf(service.default, service["a"]) + engine.engines).forEach { it.coroutineContext.job.join() }
      assert(engine.engines.size == 2)
      assert(runCatching { service.default.get("/") }.isFailure)
    }

    test("the clients start in the infrastructure phase, before the services that use them, and stop after them") {
      val clients = KtorClientService(KtorClientsConfig(), MockClientEngine())
      val caller = object : AppService {}
      assert(startPlan(listOf(caller, clients)) == listOf(clients, caller))
      clients.shutdown()
    }

    test("a client that fails to build closes the ones already built") {
      val engine = MockClientEngine()
      val badTls = KtorClientConfig(tls = KtorClientTlsConfig(trustStore = KeyStoreConfig(path = "/no/such/store")))
      val failure = runCatching { KtorClientService(KtorClientsConfig(named = mapOf("bad" to badTls)), engine) }
      assert(failure.isFailure)
      engine.engines.single().coroutineContext.job.join()
    }

    test("a named client can't be called default") {
      val failure = runCatching {
        KtorClientService(KtorClientsConfig(named = mapOf("default" to KtorClientConfig())), MockClientEngine())
      }.exceptionOrNull()
      assert(failure is IllegalArgumentException)
    }
  }

  testSuite("named clients") {
    test("are looked up by name, or fall back to the default with getOrDefault") {
      val service = KtorClientService(KtorClientsConfig(named = mapOf("a" to KtorClientConfig())), MockClientEngine())
      try {
        assert(service.names == setOf("a"))
        assert(service["a"] !== service.default)
        assert(service.getOrDefault("a") === service["a"])
        assert(service.getOrDefault("b") === service.default)
        assert(runCatching { service["b"] }.exceptionOrNull() is NoSuchElementException)
      } finally {
        service.shutdown()
      }
    }

    test("take what they leave unset from the default client, and add to its headers") {
      val config = KtorClientsConfig(
        default = KtorClientConfig(
          baseUrl = "https://default.test/",
          requestTimeout = 10.seconds,
          headers = mapOf("A" to Masked("default"), "B" to Masked("default")),
          retry = KtorClientRetryConfig(maxRetries = 2),
        ),
        named = mapOf("x" to KtorClientConfig(baseUrl = "https://x.test/api/", headers = mapOf("B" to Masked("x")))),
      )
      assert(
        config.resolve("x") == KtorClientConfig(
          baseUrl = "https://x.test/api/",
          requestTimeout = 10.seconds,
          headers = mapOf("A" to Masked("default"), "B" to Masked("x")),
          retry = KtorClientRetryConfig(maxRetries = 2),
        )
      )
      assert(config.resolve("default") == config.default)
    }
  }

  testSuite("contributed defaults") {
    val trust = KtorClientTlsConfig(trustStore = TestCertificate("ca", io.ktor.network.tls.certificates.KeyType.Server).trustStore().inline())
    val module = KtorClientDefaults {
      mapOf(
        "platform" to KtorClientConfig(
          baseUrl = "https://platform.test/",
          requestTimeout = 5.seconds,
          headers = mapOf("A" to Masked("module")),
          tls = trust,
        ),
      )
    }

    suspend fun specs(app: KtorClientsConfig, vararg defaults: KtorClientDefaults): Map<String, KtorClientSpec> {
      val engine = MockClientEngine()
      KtorClientService(app, engine, defaults = defaults.toList()).shutdown()
      return engine.specs.associateBy { it.name }
    }

    test("make a named client the application doesn't configure, over the default client's settings") {
      val platform = specs(KtorClientsConfig(KtorClientConfig(connectTimeout = 1.seconds)), module).getValue("platform")
      assert(platform.config.baseUrl == "https://platform.test/")
      assert(platform.config.requestTimeout == 5.seconds)
      assert(platform.config.connectTimeout == 1.seconds)
      assert(platform.tls?.trustManager != null)
    }

    test("are overridden by the application's settings, field by field") {
      val app = KtorClientsConfig(
        KtorClientConfig(connectTimeout = 1.seconds),
        mapOf("platform" to KtorClientConfig(baseUrl = "https://elsewhere.test/", headers = mapOf("B" to Masked("app")))),
      )
      val platform = specs(app, module).getValue("platform")
      assert(platform.config.baseUrl == "https://elsewhere.test/")
      assert(platform.config.requestTimeout == 5.seconds)
      assert(platform.config.connectTimeout == 1.seconds)
      assert(platform.config.headers == mapOf("A" to Masked("module"), "B" to Masked("app")))
      assert(platform.tls?.trustManager != null)
    }

    test("have their tls replaced whole by the application's") {
      val app = KtorClientsConfig(named = mapOf("platform" to KtorClientConfig(tls = KtorClientTlsConfig())))
      assert(specs(app, module).getValue("platform").tls == null)
    }

    test("of the same client from two contributors fail, naming both") {
      val other = KtorClientDefaults { mapOf("platform" to KtorClientConfig()) }
      val failure = runCatching { specs(KtorClientsConfig(), module, other) }.exceptionOrNull()
      assert(failure is IllegalArgumentException)
      assert(failure!!.message!!.contains("HTTP client platform"))
    }

    test("can't name the default client") {
      val failure = runCatching { specs(KtorClientsConfig(), KtorClientDefaults { mapOf("default" to KtorClientConfig()) }) }
      assert(failure.exceptionOrNull() is IllegalArgumentException)
    }
  }

  testSuite("settings") {
    test("the base URL and default headers are applied, and a request's own header wins") {
      val engine = MockClientEngine()
      val config = KtorClientConfig(
        baseUrl = "https://api.test/v1/",
        headers = mapOf("X-Api-Key" to Masked("secret"), "Accept" to Masked("application/json")),
      )
      val service = KtorClientService(KtorClientsConfig(named = mapOf("api" to config)), engine)
      try {
        service["api"].get("users") { header(HttpHeaders.Accept, "text/plain") }
        val request = engine.requests.single()
        assert(request.url.toString() == "https://api.test/v1/users")
        assert(request.headers["X-Api-Key"] == "secret")
        assert(request.headers.getAll(HttpHeaders.Accept) == listOf("text/plain"))
      } finally {
        service.shutdown()
      }
    }

    test("the timeouts are applied") {
      val engine = MockClientEngine()
      val config = KtorClientConfig(connectTimeout = 1.seconds, requestTimeout = 2.seconds, socketTimeout = 3.seconds)
      val service = KtorClientService(KtorClientsConfig(config), engine)
      try {
        service.default.get("https://api.test/")
        val timeout = engine.requests.single().getCapabilityOrNull(HttpTimeoutCapability)!!
        assert(timeout.connectTimeoutMillis == 1_000L)
        assert(timeout.requestTimeoutMillis == 2_000L)
        assert(timeout.socketTimeoutMillis == 3_000L)
      } finally {
        service.shutdown()
      }
    }

    test("no timeouts are set unless configured, leaving the engine's") {
      val engine = MockClientEngine()
      val service = KtorClientService(KtorClientsConfig(), engine)
      try {
        service.default.get("https://api.test/")
        assert(engine.requests.single().getCapabilityOrNull(HttpTimeoutCapability) == null)
      } finally {
        service.shutdown()
      }
    }

    test("the engine is given each client's name, resolved settings, and TLS material") {
      val engine = MockClientEngine()
      val tls = KtorClientTlsConfig(trustStore = TestCertificate("server", io.ktor.network.tls.certificates.KeyType.Server).trustStore().inline())
      val service = KtorClientService(
        KtorClientsConfig(KtorClientConfig(requestTimeout = 1.seconds), mapOf("a" to KtorClientConfig(tls = tls))),
        engine,
      )
      service.shutdown()
      val (default, a) = engine.specs
      assert(default.name == "default" && default.tls == null)
      assert(a.name == "a" && a.config.requestTimeout == 1.seconds && a.tls?.trustManager != null)
      assert(a.tls?.hasClientCertificate == false)
    }

    test("customizers run for each client, by name, after the typed settings") {
      val engine = MockClientEngine()
      val names = mutableListOf<String>()
      val service = KtorClientService(
        KtorClientsConfig(KtorClientConfig(headers = mapOf("X" to Masked("config"))), mapOf("a" to KtorClientConfig())),
        engine,
        KtorClientCustomizer { name, config ->
          names += name
          config.defaultRequest { headers.append("X-Client", name) }
        },
      )
      try {
        service["a"].get("https://api.test/")
        assert(names == listOf("default", "a"))
        assert(engine.requests.single().headers["X-Client"] == "a")
      } finally {
        service.shutdown()
      }
    }
  }

  testSuite("retry") {
    test("classifies 5xx, 408 and 429 as transient, and other 4xx as permanent") {
      val transient = listOf(500, 502, 503, 504, 408, 429)
      val permanent = listOf(400, 401, 403, 404, 409, 422)
      assert(transient.all { HttpStatusCode.fromValue(it).isTransient() })
      assert(permanent.none { HttpStatusCode.fromValue(it).isTransient() })
      assert(listOf(200, 204, 301).none { HttpStatusCode.fromValue(it).isTransient() })
    }

    test("classifies I/O failures and timeouts as transient, and TLS and other failures as permanent") {
      assert(IOException("reset").isTransient())
      assert(java.net.ConnectException("refused").isTransient())
      assert(!javax.net.ssl.SSLHandshakeException("untrusted").isTransient())
      assert(!kotlinx.coroutines.CancellationException("cancelled").isTransient())
      assert(!IllegalStateException("bug").isTransient())
    }

    suspend fun calls(retry: KtorClientRetryConfig?, method: HttpMethod, handler: MockRequestHandler): Pair<HttpStatusCode, Int> {
      val engine = MockClientEngine(handler)
      val service = KtorClientService(KtorClientsConfig(KtorClientConfig(retry = retry)), engine)
      try {
        val status = service.default.request("https://api.test/") { this.method = method }.status
        return status to engine.requests.size
      } finally {
        service.shutdown()
      }
    }

    test("retries a transient status until it succeeds") {
      val handler = statuses(HttpStatusCode.ServiceUnavailable, HttpStatusCode.TooManyRequests)
      assert(calls(noDelay, HttpMethod.Get, handler) == (HttpStatusCode.OK to 3))
    }

    test("gives up after maxRetries") {
      val handler: MockRequestHandler = { respond("", HttpStatusCode.BadGateway) }
      assert(calls(noDelay.copy(maxRetries = 2), HttpMethod.Get, handler) == (HttpStatusCode.BadGateway to 3))
    }

    test("doesn't retry a permanent status") {
      assert(calls(noDelay, HttpMethod.Get, statuses(HttpStatusCode.NotFound)) == (HttpStatusCode.NotFound to 1))
    }

    test("doesn't retry a method that isn't idempotent, unless configured to") {
      assert(calls(noDelay, HttpMethod.Post, statuses(HttpStatusCode.ServiceUnavailable)).second == 1)
      val withPost = noDelay.copy(methods = setOf("POST"))
      assert(calls(withPost, HttpMethod.Post, statuses(HttpStatusCode.ServiceUnavailable)) == (HttpStatusCode.OK to 2))
    }

    test("retries a transient exception") {
      var failures = 1
      val handler: MockRequestHandler = { if (failures-- > 0) throw IOException("reset") else respondOk() }
      assert(calls(noDelay, HttpMethod.Get, handler) == (HttpStatusCode.OK to 2))
    }

    test("is off without retry settings, or with maxRetries 0") {
      assert(calls(null, HttpMethod.Get, statuses(HttpStatusCode.ServiceUnavailable)).second == 1)
      assert(calls(noDelay.copy(maxRetries = 0), HttpMethod.Get, statuses(HttpStatusCode.ServiceUnavailable)).second == 1)
    }
  }
}
