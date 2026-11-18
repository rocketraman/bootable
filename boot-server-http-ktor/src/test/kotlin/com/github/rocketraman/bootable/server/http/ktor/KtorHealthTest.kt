package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.AdvancedAppService
import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Health
import com.github.rocketraman.bootable.boot.HealthCheck
import com.github.rocketraman.bootable.boot.HealthStatus
import com.github.rocketraman.bootable.boot.LifecycleState
import com.github.rocketraman.bootable.boot.ReadinessConfig
import com.github.rocketraman.bootable.boot.ServiceContext
import com.github.rocketraman.bootable.boot.StartupTimeline
import com.github.rocketraman.bootable.config.common.HostPort
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** An application server, which installs [health]'s routes. */
private class AppServer(private val health: KtorHealth) :
  KtorService<CIOApplicationEngine, CIOApplicationEngine.Configuration>("app", HostPort("127.0.0.1", 0), CIO) {
  override fun Application.module() {
    installHealth(health)
    routing { get("/hello") { call.respondText("hello") } }
  }
}

/**
 * Starts and stops [services] through [health], as Bootable's controllers do: the start within its
 * [Health.aroundAppStart], with [whileStarting] run once they have started, but before the application is running;
 * and the stop after its [Health.appShutdown], which drains.
 */
private class App(private val health: Health, vararg services: AppService) {
  private val context = ServiceContext(
    services.toSet(),
    services.toList(),
    { (it as AdvancedAppService).start {} },
    { it.shutdown() },
  )

  fun start(whileStarting: () -> Unit = {}) {
    health.aroundAppStart(context, StartupTimeline(TimeSource.Monotonic.markNow())) {
      context.startPlan.forEach(context.serviceStart)
      whileStarting()
    }
  }

  fun stop() {
    health.appShutdown(context, 1_000_000) {}
    context.startPlan.asReversed().forEach(context.serviceShutdown)
    health.appCleanup()
  }
}

private val client = HttpClient.newHttpClient()

private data class Response(val status: Int, val body: String)

private fun get(port: Int, path: String): Response {
  val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).build()
  val response = client.send(request, HttpResponse.BodyHandlers.ofString())
  return Response(response.statusCode(), response.body())
}

private suspend fun KtorService<*, *>.port() = server.engine.resolvedConnectors().single().port

val KtorHealthTest by testSuite {
  test("serves liveness and readiness on the application's server, following the lifecycle and the checks") {
    var dbUp = true
    val health = Health(
      setOf(HealthCheck("db") { if (dbUp) HealthStatus.Healthy else HealthStatus.Unhealthy("db is down") }),
      ReadinessConfig(drainDelay = 500.milliseconds),
    )
    val server = AppServer(KtorHealth(health))
    val app = App(health, server)

    app.start {
      val port = kotlinx.coroutines.runBlocking { server.port() }
      assert(get(port, "/health/live") == Response(200, "UP\nstate: Starting\n"))
      assert(get(port, "/health/ready") == Response(503, "DOWN\nstate: Starting\n"))
    }
    val port = server.port()
    assert(get(port, "/health/live") == Response(200, "UP\nstate: Running\n"))
    assert(get(port, "/health/ready") == Response(200, "UP\nstate: Running\ndb: UP\n"))

    dbUp = false
    assert(get(port, "/health/ready") == Response(503, "DOWN\nstate: Running\ndb: DOWN (db is down)\n"))
    assert(get(port, "/health/live").status == 200)
    dbUp = true

    // during the drain, the server still serves, but isn't ready
    val stopping = thread { app.stop() }
    val mark = TimeSource.Monotonic.markNow()
    while (health.state != LifecycleState.Stopping) {
      check(mark.elapsedNow() < 10.seconds)
      Thread.sleep(5)
    }
    assert(get(port, "/health/ready") == Response(503, "DOWN\nstate: Stopping\n"))
    assert(get(port, "/health/live") == Response(200, "UP\nstate: Stopping\n"))
    assert(get(port, "/hello") == Response(200, "hello"))
    stopping.join()
    assert(health.state == LifecycleState.Stopped)
  }

  test("serves them on a listener of their own, which starts first, when hostPort is set") {
    val health = Health()
    val ktorHealth = KtorHealth(health, KtorHealthConfig(livePath = "/live", readyPath = "/ready", hostPort = "127.0.0.1:0"))
    val listener = ktorHealth.listener(CIO)
    assert(listener is HealthKtorService<*, *>)
    assert(listener.ordering().phase == DefaultPhase.Telemetry)
    assert(ktorHealth.config.applicationServerPaths.isEmpty())
    val server = AppServer(ktorHealth)
    val app = App(health, listener, server)

    app.start()
    try {
      val healthPort = (listener as HealthKtorService<*, *>).port()
      assert(get(healthPort, "/live").status == 200)
      assert(get(healthPort, "/ready") == Response(200, "UP\nstate: Running\n"))
      // not on the application's server
      assert(get(server.port(), "/ready").status == 404)
    } finally {
      app.stop()
    }
  }

  test("has no listener of its own, and serves on the application's server, by default") {
    val ktorHealth = KtorHealth(Health())
    assert(ktorHealth.listener(CIO) !is HealthKtorService<*, *>)
    assert(ktorHealth.config.applicationServerPaths == setOf("/health/live", "/health/ready"))
  }
}
