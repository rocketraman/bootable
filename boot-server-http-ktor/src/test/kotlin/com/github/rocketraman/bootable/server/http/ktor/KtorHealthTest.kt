package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.AdvancedAppService
import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BuildInfo
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

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
    { (it as AdvancedAppService).start(CoroutineScope(SupervisorJob())) },
    { it.shutdown() },
  )

  suspend fun start(whileStarting: suspend () -> Unit = {}) {
    health.aroundAppStart(context, StartupTimeline(TimeSource.Monotonic.markNow())) {
      context.startPlan.forEach { context.serviceStart(it) }
      whileStarting()
    }
  }

  suspend fun stop() {
    health.appShutdown(context, 1.hours)
    context.startPlan.asReversed().forEach { context.serviceShutdown(it) }
    health.appCleanup()
  }
}

private val client = HttpClient.newHttpClient()

private data class Response(
  val status: Int,
  val body: String,
  val contentType: String? = null,
  val cacheControl: String? = null,
)

private fun get(port: Int, path: String): Response {
  val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).build()
  val response = client.send(request, HttpResponse.BodyHandlers.ofString())
  return Response(
    response.statusCode(),
    response.body(),
    response.headers().firstValue("Content-Type").orElse(null),
    response.headers().firstValue("Cache-Control").orElse(null),
  )
}

/** A health route's answer: its status, and its body, parsed as JSON. */
private data class HealthResponse(val status: Int, val body: JsonElement)

private fun getHealth(port: Int, path: String): HealthResponse {
  val response = get(port, path)
  assert(response.contentType?.startsWith("application/json") == true) { "Content-Type: ${response.contentType}" }
  return HealthResponse(response.status, Json.parseToJsonElement(response.body))
}

private fun health(status: Int, json: String) = HealthResponse(status, Json.parseToJsonElement(json))

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
      val port = server.port()
      assert(getHealth(port, "/health/live") == health(200, """{"status": "UP", "state": "Starting"}"""))
      assert(getHealth(port, "/health/ready") == health(503, """{"status": "DOWN", "state": "Starting", "checks": {}}"""))
    }
    val port = server.port()
    assert(getHealth(port, "/health/live") == health(200, """{"status": "UP", "state": "Running"}"""))
    assert(getHealth(port, "/health/ready") == health(200, """{"status": "UP", "state": "Running", "checks": {"db": {"status": "UP"}}}"""))

    dbUp = false
    val down = getHealth(port, "/health/ready")
    assert(
      down == health(
        503,
        """{"status": "DOWN", "state": "Running", "checks": {"db": {"status": "DOWN", "reason": "db is down"}}}""",
      ),
    )
    // a consumer can check one service
    assert(down.body.jsonObject["checks"]!!.jsonObject["db"]!!.jsonObject["status"]!!.jsonPrimitive.content == "DOWN")
    assert(get(port, "/health/live").status == 200)
    dbUp = true

    // during the drain, the server still serves, but isn't ready
    val stopping = thread { runBlocking { app.stop() } }
    val mark = TimeSource.Monotonic.markNow()
    while (health.state != LifecycleState.Stopping) {
      check(mark.elapsedNow() < 10.seconds)
      Thread.sleep(5)
    }
    assert(getHealth(port, "/health/ready") == health(503, """{"status": "DOWN", "state": "Stopping", "checks": {}}"""))
    assert(getHealth(port, "/health/live") == health(200, """{"status": "UP", "state": "Stopping"}"""))
    assert(get(port, "/hello").let { it.status == 200 && it.body == "hello" })
    stopping.join()
    assert(health.state == LifecycleState.Stopped)
  }

  test("serves them on a listener of their own, which starts first, when hostPort is set") {
    val health = Health()
    val ktorHealth = KtorHealth(
      health,
      KtorHealthConfig(livePath = "/live", readyPath = "/ready", infoPath = "/info", hostPort = "127.0.0.1:0"),
      BuildInfo(version = "1.4.2"),
    )
    val listener = ktorHealth.listener(CIO)
    assert(listener is HealthKtorService<*, *>)
    assert(listener.ordering().phase == DefaultPhase.Telemetry)
    assert(ktorHealth.config.applicationServerPaths.isEmpty())
    val server = AppServer(ktorHealth)
    val app = App(health, listener, server)

    app.start()
    try {
      val healthPort = (listener as HealthKtorService<*, *>).port()
      assert(getHealth(healthPort, "/live") == health(200, """{"status": "UP", "state": "Running"}"""))
      assert(getHealth(healthPort, "/ready") == health(200, """{"status": "UP", "state": "Running", "checks": {}}"""))
      assert(getHealth(healthPort, "/info") == health(200, """{"version": "1.4.2"}"""))
      // not on the application's server
      assert(get(server.port(), "/ready").status == 404)
      assert(get(server.port(), "/info").status == 404)
    } finally {
      app.stop()
    }
  }

  test("has no listener of its own, and serves on the application's server, by default") {
    val ktorHealth = KtorHealth(Health())
    assert(ktorHealth.listener(CIO) !is HealthKtorService<*, *>)
    assert(ktorHealth.config.applicationServerPaths == setOf("/health/live", "/health/ready", "/health/info"))
  }

  test("serves the build info, whatever the lifecycle state, which no cache keeps") {
    val health = Health()
    val buildInfo = BuildInfo(version = "1.4.2", revision = "3f2c9e1", attributes = mapOf("branch" to "main"))
    val server = AppServer(KtorHealth(health, buildInfo = buildInfo))
    val app = App(health, server)

    app.start {
      val port = kotlinx.coroutines.runBlocking { server.port() }
      assert(getHealth(port, "/health/info").status == 200)
    }
    try {
      val port = server.port()
      assert(
        getHealth(port, "/health/info") ==
          health(200, """{"version": "1.4.2", "revision": "3f2c9e1", "attributes": {"branch": "main"}}"""),
      )
      assert(get(port, "/health/info").cacheControl == "no-store")
    } finally {
      app.stop()
    }
  }
}
