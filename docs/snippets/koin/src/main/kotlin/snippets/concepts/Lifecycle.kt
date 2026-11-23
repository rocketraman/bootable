package snippets.concepts

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.server.http.ktor.CioKtorService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class HelloServer(config: HostPort) : CioKtorService("hello", config) {
  override fun Application.module() {
    routing {
      get("/") { call.respondText("hello") }
    }
  }
}

// tag::wire[]
fun main() = boot {
  val koin = koinApplication {
    modules(
      bootableModule(),  // Bootable, its default controllers, and every AppService and LifecycleController definition
      module { single<AppService>(named<HelloServer>()) { HelloServer(HostPort(port = 8000)) } },
    )
  }.koin
  koin.get<Bootable>()
}
// end::wire[]
