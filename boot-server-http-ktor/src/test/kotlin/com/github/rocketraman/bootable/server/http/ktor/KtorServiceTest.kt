package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.config.common.HostPort
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

val KtorServiceTest by testSuite {
  testSuite("CIO") { engineTests(CIO) }
  testSuite("Netty") { engineTests(Netty) }
}

/**
 * The same lifecycle tests on each engine: the service is started and stopped as Bootable does, through its
 * [AppService] interface, and reached over real HTTP.
 */
private fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> TestSuite.engineTests(
  engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
) {
  test("serves once started, and stops serving once shut down") {
    val service = HelloService(engineFactory)
    val appService: AppService = service
    appService.start(CoroutineScope(SupervisorJob()))
    val port = service.port()
    try {
      assert(get(port, "/hello") == "hello")
    } finally {
      appService.shutdown()
    }
    assert(runCatching { get(port, "/hello") }.exceptionOrNull() is IOException)
  }

  test("listens on the configured host") {
    withStarted(HelloService(engineFactory)) {
      val connectors = server.engine.resolvedConnectors()
      assert(connectors.size == 1)
      assert(connectors.single().host == "127.0.0.1")
    }
  }

  test("applies the default shutdown grace period and timeout") {
    withStarted(HelloService(engineFactory)) {
      assert(server.engineConfig.shutdownGracePeriod == KtorService.DEFAULT_SHUTDOWN_GRACE_PERIOD)
      assert(server.engineConfig.shutdownTimeout == KtorService.DEFAULT_SHUTDOWN_TIMEOUT)
    }
  }

  test("configureEngine runs after the defaults, on the engine's own configuration") {
    var configured: TConfiguration? = null
    val service = HelloService(engineFactory) {
      configured = this
      shutdownGracePeriod = 10
      shutdownTimeout = 20
    }
    withStarted(service) {
      assert(configured === server.engineConfig)
      assert(server.engineConfig.shutdownGracePeriod == 10L)
      assert(server.engineConfig.shutdownTimeout == 20L)
      assert(server.engineConfig.connectors.size == 1)
    }
  }

  test("a route can fail the service's scope, which stops the application") {
    val failure = CompletableDeferred<Throwable>()
    val service = HelloService(engineFactory)
    service.start(CoroutineScope(Job() + CoroutineExceptionHandler { _, e -> failure.complete(e) }))
    try {
      assert(get(service.port(), "/die") == "dying")
      assert(withTimeout(10.seconds) { failure.await() }.message == "died")
    } finally {
      service.shutdown()
    }
  }

  test("shutdown before start does nothing") {
    HelloService(engineFactory).shutdown()
  }
}

private class HelloService<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
  engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
  private val configure: TConfiguration.() -> Unit = {},
) : KtorService<TEngine, TConfiguration>("hello", HostPort("127.0.0.1", 0), engineFactory) {
  override fun TConfiguration.configureEngine() = configure()

  override fun Application.module() {
    routing {
      get("/hello") { call.respondText("hello") }
      get("/die") {
        this@HelloService.scope.launch { error("died") }
        call.respondText("dying")
      }
    }
  }
}

private suspend fun <S : KtorService<*, *>> withStarted(service: S, block: suspend S.() -> Unit) {
  service.start(CoroutineScope(SupervisorJob()))
  try {
    service.block()
  } finally {
    service.shutdown()
  }
}

/** The port the server bound, since the tests ask for any free port. */
private suspend fun KtorService<*, *>.port() = server.engine.resolvedConnectors().single().port

private val client = HttpClient.newHttpClient()

private fun get(port: Int, path: String): String {
  val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).build()
  val response = client.send(request, HttpResponse.BodyHandlers.ofString())
  assert(response.statusCode() == 200)
  return response.body()
}
