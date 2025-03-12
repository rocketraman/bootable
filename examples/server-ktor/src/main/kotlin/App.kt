import com.github.rocketraman.bootable.boot.bindAppService
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.bindConfig
import com.github.rocketraman.bootable.config.configModule
import com.github.rocketraman.bootable.server.http.ktor.KtorService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import org.apache.logging.log4j.kotlin.logger
import org.kodein.di.DI
import org.kodein.di.new
import org.kodein.di.singleton

fun HTML.index() {
  head {
    title("Hello from Ktor!")
  }
  body {
    div {
      +"Hello from Ktor"
    }
    div {
      p {
        a(href = "/hello") {
          +"/Hello"
        }
      }
      p {
        a(href = "/die") {
          +"/Die"
        }
      }
    }
  }
}

class ExampleServer(config: ServerConfig, private val hello: HelloConfig) : KtorService("test", config) {
  private val logger = logger()

  override fun Application.module() {
    routing {
      get("/") {
        logger.info { "/ called, returning index" }
        call.respondHtml(HttpStatusCode.OK, HTML::index)
      }

      get("/hello") {
        logger.info { "/hello called, returning response ${hello.response}" }
        call.respond(HttpStatusCode.OK, hello.response)
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

  bindConfig<HelloConfig>("hello")

  bindConfig<ServerConfig>("server")

  bindAppService { singleton { new(::ExampleServer) } }
}

fun main() {
  boot {
    import(serverModule)
  }
}
