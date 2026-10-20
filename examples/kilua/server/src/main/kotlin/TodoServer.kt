import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.server.http.ktor.NettyKtorService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.logging.log4j.kotlin.logger

data class ServerConfig(override val hostPort: String) : HostPort

/**
 * Serves the page at `/`, rendered with the current todos by the [renderer], the client's files, and the JSON API the
 * client calls under [TODOS_PATH]:
 *
 * - `GET /api/todos` lists every todo, oldest first
 * - `POST /api/todos` adds a [NewTodo], returning the stored [Todo]
 * - `PATCH /api/todos/{id}` applies a [TodoUpdate], returning the updated [Todo], or 404
 * - `DELETE /api/todos/{id}` removes a todo, or 404
 *
 * The API is the compose-html example's, and the models are the shared module's, so the client and the server agree on
 * the JSON without any schema in between. The client calls the API from Node.js too, to render the page.
 */
class TodoServer(
  config: ServerConfig,
  private val todos: TodoRepository,
  private val renderer: SsrRenderer,
) : NettyKtorService("todos", config) {
  private val logger = logger()

  override fun Application.module() {
    install(ContentNegotiation) {
      json()
    }

    routing {
      get("/") {
        // Kilua's renderer only reruns a route, and so reloads the todos, when the path it's asked for changes, so the
        // path carries the todos' revision; the client's router matches on "/" and ignores it
        call.respondText(renderer.render("/?revision=${todos.revision}"), ContentType.Text.Html)
      }

      // the client's bundle, which the build copies onto the classpath; not its index.html at `/`, which is rendered above
      staticResources("/", "web", index = null)

      route(TODOS_PATH) {
        get {
          call.respond(todos.all())
        }

        post {
          val title = call.receive<NewTodo>().title.trim()
          // the client checks this too, but a server can't trust its clients
          val problem = titleProblem(title)
          if (problem != null) {
            call.respond(HttpStatusCode.BadRequest, problem)
            return@post
          }
          val todo = todos.add(title)
          logger.info { "Added todo ${todo.id}" }
          call.respond(HttpStatusCode.Created, todo)
        }

        patch("/{id}") {
          val id = call.parameters["id"]?.toLongOrNull()
          val todo = id?.let { todos.update(it, call.receive<TodoUpdate>().done) }
          if (todo == null) call.respond(HttpStatusCode.NotFound) else call.respond(todo)
        }

        delete("/{id}") {
          val deleted = call.parameters["id"]?.toLongOrNull()?.let { todos.delete(it) } ?: false
          call.respond(if (deleted) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }
      }
    }
  }
}
