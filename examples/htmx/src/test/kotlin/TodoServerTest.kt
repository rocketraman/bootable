import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.htmx.*
import io.ktor.http.*
import io.ktor.server.testing.*

/** Runs the server's routes, wired as in `main` but without Bootable, with a client that makes requests as htmx does. */
private fun withServer(block: suspend HttpClient.() -> Unit) = testApplication {
  val server = TodoServer(ServerConfig("127.0.0.1:0"), TodoRepository())
  application { with(server) { module() } }
  createClient {}.block()
}

private fun HttpRequestBuilder.fromHtmx(vararg form: Pair<String, String>) {
  header(HxRequestHeaders.Request, "true")
  setBody(FormDataContent(parameters { form.forEach { (name, value) -> append(name, value) } }))
}

private suspend fun HttpClient.add(title: String) = post("/todos") { fromHtmx("title" to title) }

private suspend fun HttpClient.page() = get("/").bodyAsText()

val TodoServerTest by testSuite {
  test("serves the page, rendered, with htmx") {
    withServer {
      add("Write an example")
      val page = get("/")
      assert(page.status == HttpStatusCode.OK)
      val html = page.bodyAsText()
      assert(html.startsWith("<!DOCTYPE html>"))
      assert("""<script src="/assets/htmx.min.js">""" in html)
      assert("Write an example" in html)
      assert("0 of 1 done" in html)
    }
  }

  test("serves htmx's script and the stylesheet") {
    withServer {
      val script = get("/assets/htmx.min.js")
      assert(script.status == HttpStatusCode.OK)
      assert(script.contentType()?.match(ContentType.Text.JavaScript) == true)
      assert(get("/assets/app.css").status == HttpStatusCode.OK)
    }
  }

  test("starts with no todos") {
    withServer {
      assert("Nothing to do." in page())
    }
  }

  test("an added todo is returned as an item, with the form and count swapped out of band") {
    withServer {
      val response = add("  Write an example  ")
      assert(response.status == HttpStatusCode.OK)
      val fragment = response.bodyAsText()
      assert(!fragment.contains("<html"))
      assert("""<li class="item" id="todo-1">""" in fragment)
      assert("<span>Write an example</span>" in fragment)
      assert("""<div id="$FORM_ID" hx-swap-oob="true">""" in fragment)
      assert("""<p class="muted" id="$SUMMARY_ID" hx-swap-oob="true">0 of 1 done</p>""" in fragment)

      assert("Write an example" in page())
    }
  }

  test("a title is rendered as text") {
    withServer {
      assert("&lt;b&gt;bold&lt;/b&gt;" in add("<b>bold</b>").bodyAsText())
    }
  }

  test("a todo with an invalid title is refused, returning the form with the reason") {
    withServer {
      for (title in listOf(" ", "x".repeat(MAX_TITLE_LENGTH + 1))) {
        val response = add(title)
        assert(response.status == HttpStatusCode.UnprocessableEntity)
        assert(response.headers[HxResponseHeaders.Retarget] == "#$FORM_ID")
        assert(response.headers[HxResponseHeaders.Reswap] == HxSwap.outerHtml)
        val fragment = response.bodyAsText()
        assert("""<p class="error">${titleProblem(title)}</p>""" in fragment)
        assert("""value="${title.trim()}"""" in fragment)
      }
      assert("Nothing to do." in page())
    }
  }

  test("a todo can be marked done") {
    withServer {
      add("Write an example")

      val response = patch("/todos/1") { fromHtmx("done" to "true") }
      assert(response.status == HttpStatusCode.OK)
      val fragment = response.bodyAsText()
      assert("""<input type="checkbox" checked="checked"""" in fragment)
      assert("""<span class="done">Write an example</span>""" in fragment)
      assert("1 of 1 done" in fragment)
      assert("1 of 1 done" in page())
    }
  }

  test("a deleted todo is gone") {
    withServer {
      add("Write an example")

      val response = delete("/todos/1") { fromHtmx() }
      assert(response.status == HttpStatusCode.OK)
      assert("""<p class="muted" id="$SUMMARY_ID" hx-swap-oob="true">Nothing to do.</p>""" == response.bodyAsText())
      assert("Write an example" !in page())
    }
  }

  test("a missing todo is not found") {
    withServer {
      assert(patch("/todos/42") { fromHtmx("done" to "true") }.status == HttpStatusCode.NotFound)
      assert(delete("/todos/42") { fromHtmx() }.status == HttpStatusCode.NotFound)
      assert(delete("/todos/not-a-number") { fromHtmx() }.status == HttpStatusCode.NotFound)
    }
  }

  test("changes are only routed for htmx") {
    withServer {
      val response = post("/todos") {
        setBody(FormDataContent(parameters { append("title", "Write an example") }))
      }
      assert(response.status == HttpStatusCode.NotFound)
      assert("Nothing to do." in page())
    }
  }
}
