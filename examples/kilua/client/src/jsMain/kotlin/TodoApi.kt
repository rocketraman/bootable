import dev.kilua.utils.jsGet
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import js.globals.globalThis

/** A request the server refused, with its reason. */
class ApiException(message: String) : Exception(message)

/**
 * Calls the server's API, sending and receiving the shared models as JSON, with Ktor's client, as the compose-html
 * example does; Kilua's own REST client has no `PATCH`.
 *
 * In the browser, the paths are relative, so the client calls whichever server it was loaded from. In Node.js, where
 * the server's renderer runs the client to render a page, there is no page to be relative to, so they're prefixed with
 * the server's URL, which the renderer passes to Kilua as `--rpc-url-prefix`.
 */
class TodoApi(
  private val client: HttpClient = HttpClient {
    install(ContentNegotiation) {
      json()
    }
    HttpResponseValidator {
      validateResponse { response ->
        if (!response.status.isSuccess()) {
          throw ApiException(response.bodyAsText().ifBlank { response.status.toString() })
        }
      }
    }
  },
) {
  // read on each call, since Kilua sets it once its SSR router starts, after this is created
  private val base: String
    get() = globalThis.jsGet("rpc_url_prefix")?.toString().orEmpty()

  suspend fun all(): List<Todo> = client.get("$base$TODOS_PATH").body()

  suspend fun add(title: String): Todo = client.post("$base$TODOS_PATH") {
    contentType(ContentType.Application.Json)
    setBody(NewTodo(title))
  }.body()

  suspend fun update(id: Long, done: Boolean): Todo = client.patch("$base$TODOS_PATH/$id") {
    contentType(ContentType.Application.Json)
    setBody(TodoUpdate(done))
  }.body()

  suspend fun delete(id: Long) {
    client.delete("$base$TODOS_PATH/$id")
  }
}
