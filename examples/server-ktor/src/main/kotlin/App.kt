import com.github.rocketraman.bootable.boot.bindAppService
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.bindConfig
import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.config.common.host
import com.github.rocketraman.bootable.config.configModule
import com.github.rocketraman.bootable.server.http.ktor.KtorService
import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.application.*
import kotlinx.html.*
import org.apache.logging.log4j.kotlin.logger
import org.kodein.di.DI
import org.kodein.di.instance
import org.kodein.di.singleton

fun HTML.index() {
  head {
    title("Hello from Ktor!")
  }
  body {
    div {
      +"Hello from Ktor"
    }
  }
}

class ExampleServer(config: ServerConfig) : KtorService("test", config) {
  private val logger = logger()

  override fun Application.module() {
    routing {
      get("/") {
        call.respondHtml(HttpStatusCode.OK, HTML::index)
      }

      get("/die") {
        call.respond(HttpStatusCode.OK,"Dying! \uD83D\uDE10")
        logger.info { "Dying! \uD83D\uDE10" }
        die()
      }
    }
  }
}

val serverModule = DI.Module("serverModule") {
  import(configModule)

  bindConfig<ServerConfig>("server")

  bindAppService() with singleton { ExampleServer(instance<ServerConfig>()) }
}

fun main() {
  boot {
    import(serverModule)
  }
}
