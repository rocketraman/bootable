import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

/** A request the server refused, with its reason. */
class ApiException(message: String) : Exception(message)

/**
 * Calls the server's API, sending and receiving the shared models as JSON. The paths are relative, so the client calls
 * whichever server it was loaded from.
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
  suspend fun all(): List<Todo> = client.get(TODOS_PATH).body()

  suspend fun add(title: String): Todo = client.post(TODOS_PATH) {
    contentType(ContentType.Application.Json)
    setBody(NewTodo(title))
  }.body()

  suspend fun update(id: Long, done: Boolean): Todo = client.patch("$TODOS_PATH/$id") {
    contentType(ContentType.Application.Json)
    setBody(TodoUpdate(done))
  }.body()

  suspend fun delete(id: Long) {
    client.delete("$TODOS_PATH/$id")
  }
}
