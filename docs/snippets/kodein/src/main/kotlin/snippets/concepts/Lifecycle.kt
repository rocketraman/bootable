package snippets.concepts

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.bindAppService
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.server.http.ktor.CioKtorService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.di.singleton

class HelloServer(config: HostPort) : CioKtorService("hello", config) {
  override fun Application.module() {
    routing {
      get("/") { call.respondText("hello") }
    }
  }
}

// tag::wire[]
fun main() = boot {
  val di = DI {
    import(bootableModule)       // Bootable, its default controllers, and the sets to contribute into
    bindAppService { singleton { HelloServer(HostPort(port = 8000)) } }
  }
  di.direct.instance<Bootable>()
}
// end::wire[]
