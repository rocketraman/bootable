import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*

/**
 * Runs the server's routes, wired as in `main` but without Bootable, with a client that speaks the shared models.
 *
 * The renderer isn't started, so these tests leave `/` alone; [ServerRenderingTest] renders pages.
 */
private fun withServer(block: suspend HttpClient.() -> Unit) = testApplication {
  val renderer = SsrRenderer(SsrConfig(node = "node"), serverUrl = "http://127.0.0.1:0")
  val server = TodoServer(ServerConfig("127.0.0.1:0"), TodoRepository(), renderer)
  application { with(server) { module() } }
  createClient {
    install(ContentNegotiation) { json() }
  }.block()
}

private suspend fun HttpClient.add(title: String) = post(TODOS_PATH) {
  contentType(ContentType.Application.Json)
  setBody(NewTodo(title))
}

val TodoServerTest by testSuite {
  test("serves the client") {
    withServer {
      assert(get("/main.bundle.js").status == HttpStatusCode.OK)
      assert(get("/app.css").status == HttpStatusCode.OK)
    }
  }

  test("starts with no todos") {
    withServer {
      assert(get(TODOS_PATH).body<List<Todo>>().isEmpty())
    }
  }

  test("an added todo is listed") {
    withServer {
      val response = add("  Write an example  ")
      assert(response.status == HttpStatusCode.Created)
      val todo = response.body<Todo>()
      assert(todo.title == "Write an example")
      assert(!todo.done)

      assert(get(TODOS_PATH).body<List<Todo>>() == listOf(todo))
    }
  }

  test("a todo with an invalid title is refused with the shared rule's reason") {
    withServer {
      for (title in listOf(" ", "x".repeat(MAX_TITLE_LENGTH + 1))) {
        val response = add(title)
        assert(response.status == HttpStatusCode.BadRequest)
        assert(response.bodyAsText() == titleProblem(title))
      }
      assert(get(TODOS_PATH).body<List<Todo>>().isEmpty())
    }
  }

  test("a todo can be marked done") {
    withServer {
      val todo = add("Write an example").body<Todo>()

      val response = patch("$TODOS_PATH/${todo.id}") {
        contentType(ContentType.Application.Json)
        setBody(TodoUpdate(done = true))
      }
      assert(response.body<Todo>() == todo.copy(done = true))
      assert(get(TODOS_PATH).body<List<Todo>>() == listOf(todo.copy(done = true)))
    }
  }

  test("a deleted todo is gone") {
    withServer {
      val todo = add("Write an example").body<Todo>()

      assert(delete("$TODOS_PATH/${todo.id}").status == HttpStatusCode.NoContent)
      assert(get(TODOS_PATH).body<List<Todo>>().isEmpty())
    }
  }

  test("a missing todo is not found") {
    withServer {
      val update = patch("$TODOS_PATH/42") {
        contentType(ContentType.Application.Json)
        setBody(TodoUpdate(done = true))
      }
      assert(update.status == HttpStatusCode.NotFound)
      assert(delete("$TODOS_PATH/42").status == HttpStatusCode.NotFound)
      assert(delete("$TODOS_PATH/not-a-number").status == HttpStatusCode.NotFound)
    }
  }
}
