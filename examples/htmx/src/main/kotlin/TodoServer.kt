import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.server.http.ktor.NettyKtorService
import io.ktor.htmx.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.htmx.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.logging.log4j.kotlin.logger

data class ServerConfig(override val hostPort: String) : HostPort

/**
 * Serves the page at `/`, and the routes its htmx attributes call, which answer with fragments of it:
 *
 * - `POST /todos` adds a todo, returning its item, or the form with the reason it was refused
 * - `PATCH /todos/{id}` marks a todo done or not, returning its item, or 404
 * - `DELETE /todos/{id}` removes a todo, or 404
 *
 * Fragments that change other parts of the page, such as the count of todos done, are swapped in out of band, by id.
 */
class TodoServer(
  config: ServerConfig,
  private val todos: TodoRepository,
) : NettyKtorService("todos", config) {
  private val logger = logger()

  override fun Application.module() {
    routing {
      // the stylesheet, and htmx's script, which the build copies onto the classpath
      staticResources("/assets", "web")

      get("/") {
        call.respondHtml { todoPage(todos.all()) }
      }

      // only requests made by htmx, which swaps the fragments into the page
      hx {
        route("/todos") {
          post {
            val title = call.receiveParameters()["title"].orEmpty().trim()
            val problem = titleProblem(title)
            if (problem != null) {
              // replace the form, keeping the title and showing why, rather than adding to the list
              call.response.hx[HxResponseHeaders.Retarget] = "#$FORM_ID"
              call.response.hx[HxResponseHeaders.Reswap] = HxSwap.outerHtml
              call.respondHtmlPartial(HttpStatusCode.UnprocessableEntity) { addForm(title, problem) }
              return@post
            }
            val todo = todos.add(title)
            logger.info { "Added todo ${todo.id}" }
            call.respondHtmlPartial {
              todoItem(todo)
              addForm(oob = true)
              summary(todos.all(), oob = true)
            }
          }

          patch("/{id}") {
            val done = call.receiveParameters()["done"].toBoolean()
            val todo = call.parameters["id"]?.toLongOrNull()?.let { todos.update(it, done) }
            if (todo == null) {
              call.respond(HttpStatusCode.NotFound)
              return@patch
            }
            call.respondHtmlPartial {
              todoItem(todo)
              summary(todos.all(), oob = true)
            }
          }

          delete("/{id}") {
            val deleted = call.parameters["id"]?.toLongOrNull()?.let { todos.delete(it) } ?: false
            if (!deleted) {
              call.respond(HttpStatusCode.NotFound)
              return@delete
            }
            // the button's swap removes the item itself
            call.respondHtmlPartial { summary(todos.all(), oob = true) }
          }
        }
      }
    }
  }
}
