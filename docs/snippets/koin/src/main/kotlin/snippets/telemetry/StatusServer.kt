package snippets.telemetry

import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.server.http.ktor.CioKtorService
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.server.http.ktor.installTelemetry
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

data class StatusServerConfig(override val hostPort: String) : HostPort

/** A server whose requests are traced and measured, and which serves the metrics scrape at `/metrics`. */
class StatusServer(config: StatusServerConfig, private val telemetry: KtorTelemetry) : CioKtorService("status", config) {
  override fun Application.module() {
    // tag::server[]
    installTelemetry(telemetry)  // traces each request, and serves the scrape at telemetry.ktor.path
    // end::server[]
    routing {
      get("/") { call.respondText("ok") }
    }
  }
}
