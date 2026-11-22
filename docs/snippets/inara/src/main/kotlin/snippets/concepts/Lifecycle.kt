package snippets.concepts

import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.EmptyConfigModule
import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.server.http.ktor.CioKtorService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

class HelloServer(config: HostPort) : CioKtorService("hello", config) {
  override fun Application.module() {
    routing {
      get("/") { call.respondText("hello") }
    }
  }
}

// tag::wire[]
@DIModule(composed = true)
interface AppModule : ConfigModule, BootableModule {
  @AppServices val helloServer: HelloServer
}

fun main() = boot {
  AppModule(
    configModule = EmptyConfigModule,   // no config: Bootable's defaults
    bootableModule = bootableModule(),  // Bootable, its default controllers, and the sets to mark members into
    helloServer = Binding.single { HelloServer(HostPort(port = 8000)) },
  ).bootable
}
// end::wire[]
