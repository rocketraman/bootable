import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.defaultLifecycleControllers
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.server.http.ktor.KtorService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import org.apache.logging.log4j.kotlin.logger

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

/**
 * An ordinary class — no dependency-injection annotations. Its dependencies are plain constructor parameters,
 * so it is constructed directly in `main` (and just as easily in a unit test).
 */
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
        call.respond(HttpStatusCode.OK,"Dying! 😐")
        logger.info { "Dying! 😐" }
        die()
      }
    }
  }
}

/**
 * Vanilla wiring: inside the boot lambda (which runs after logging is initialized), load config, construct
 * services, and return the `Bootable` to run. Assembling the application is just plain Kotlin — no DI framework,
 * no annotations, no graph. The default lifecycle controllers (start/stop + signal handling) are used here; pass
 * your own to `Bootable(...)` to run a different lifecycle.
 */
fun main() = boot {
  val binder = hopliteConfigBinder()
  Bootable(
    appServices = setOf(
      ExampleServer(binder.config("server"), binder.config("hello")),
    )
  )
}
