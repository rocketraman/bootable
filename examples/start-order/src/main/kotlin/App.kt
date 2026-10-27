import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.server.http.ktor.CioKtorService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class ServerConfig(override val hostPort: String) : HostPort

/** The app's traffic: a single route. Like every `KtorService`, it is in the `Serving` phase. */
class HelloServer(config: ServerConfig) : CioKtorService("hello", config) {
  override fun Application.module() {
    routing {
      get("/") { call.respondText("Hello! Check for the ready file while this runs.\n") }
    }
  }
}

fun appServices(binder: ConfigBinder): Set<AppService> = setOf(
  HelloServer(binder.config("server")),
  ReadyFileService(binder.config("ready-file")),
)

/**
 * Vanilla wiring with a custom start order: [AppStartOrder] adds the `Announce` phase, after the server's, and
 * the [ReadyFileService] declares it. The logged start plan is `Serving[HelloServer] Announce[ReadyFileService]`.
 * Without the custom order, the boot fails before anything starts, since `Announce` is not one of its phases.
 */
fun main() = boot {
  Bootable(
    appServices = appServices(hopliteConfigBinder()),
    // comment out this line to see error diagnostics on boot if a service declares an unregistered phase
    startOrder = AppStartOrder,
  )
}
