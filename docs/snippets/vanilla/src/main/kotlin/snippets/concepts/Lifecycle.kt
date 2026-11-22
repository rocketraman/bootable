package snippets.concepts

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.config.common.port
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// tag::wire[]
fun main() = boot {
  Bootable(setOf(HelloServer(HostPort(port = 8000))))
}
// end::wire[]

// tag::service[]
class HelloServer(private val config: HostPort) : AppService {
  private lateinit var server: EmbeddedServer<*, *>

  override suspend fun start() {
    server = embeddedServer(CIO, port = config.port(8080)) {
      routing {
        get("/") { call.respondText("hello") }
      }
    }
    server.startSuspend()
  }

  override suspend fun shutdown() {
    server.stopSuspend(gracePeriodMillis = 50, timeoutMillis = 1_000)
  }

  override fun ordering() = Ordering(DefaultPhase.Serving)
}
// end::service[]
