package snippets.http

import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.server.http.ktor.CioKtorService
import com.github.rocketraman.bootable.server.http.ktor.KtorHealth
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.server.http.ktor.NettyKtorService
import com.github.rocketraman.bootable.server.http.ktor.installHealth
import com.github.rocketraman.bootable.server.http.ktor.installTelemetry
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// tag::config[]
data class ServerConfig(override val hostPort: String) : HostPort
// end::config[]

// tag::server[]
class GreetingServer(
  config: ServerConfig,
  private val health: KtorHealth,
) : CioKtorService("greeting", config) {
  override fun Application.module() {
    installHealth(health)  // the liveness and readiness probes
    routing {
      get("/") { call.respondText("hello") }
    }
  }
}
// end::server[]

// tag::netty[]
class NettyGreetingServer(config: ServerConfig) : NettyKtorService("greeting", config) {
  override fun NettyApplicationEngine.Configuration.configureEngine() {
    shutdownTimeout = 5_000            // the longest a stop waits, in ms
    responseWriteTimeoutSeconds = 30   // one of Netty's own options
  }

  override fun Application.module() {
    routing {
      get("/") { call.respondText("hello") }
    }
  }
}
// end::netty[]

// tag::traced-server[]
class TracedGreetingServer(
  config: ServerConfig,
  private val health: KtorHealth,
  private val telemetry: KtorTelemetry,
) : CioKtorService("greeting", config) {
  override fun Application.module() {
    installTelemetry(telemetry)  // trace and measure each request, and serve the scrape
    installHealth(health)
    routing {
      get("/") { call.respondText("hello") }
    }
  }
}
// end::traced-server[]
