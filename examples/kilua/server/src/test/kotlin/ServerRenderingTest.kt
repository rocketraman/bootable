import com.github.rocketraman.bootable.boot.AdvancedAppService
import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.startPlan
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.disable
import de.infix.testBalloon.framework.core.testSuite
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers

/** The Node.js to render with: the build's, which the Gradle test task sets, or else the one on the PATH. */
private val node = System.getenv("SSR_NODE") ?: "node"

private val nodeRuns = runCatching { ProcessBuilder(node, "--version").start().waitFor() == 0 }.getOrDefault(false)

/** Calls the app over HTTP, as a browser would. */
private class Browser(private val url: String) {
  private val http = HttpClient.newHttpClient()

  fun page(): String = http.send(HttpRequest.newBuilder(URI("$url/")).build(), BodyHandlers.ofString()).body()

  fun send(method: String, path: String, json: String) {
    val request = HttpRequest.newBuilder(URI("$url$path"))
      .method(method, BodyPublishers.ofString(json))
      .header("Content-Type", "application/json")
      .build()
    val response = http.send(request, BodyHandlers.ofString())
    check(response.statusCode() in 200..299) { "$method $path failed with ${response.statusCode()}" }
  }
}

/**
 * Starts the renderer and the server in their planned order, as Bootable does, on a free port, so the client, rendering
 * in Node.js, can call the server's API; then stops them in reverse. The renderer's Node.js runs on until the tests' JVM
 * exits, since Kilua's SsrEngine only stops it then.
 */
private fun withApp(block: Browser.() -> Unit) {
  val config = ServerConfig("127.0.0.1:${ServerSocket(0).use { it.localPort }}")
  val renderer = SsrRenderer(SsrConfig(node), config.localUrl())
  val server = TodoServer(config, TodoRepository(), renderer)
  val services = startPlan(setOf(server, renderer))
  assert(services == listOf(renderer, server))

  val started = mutableListOf<AppService>()
  try {
    for (service in services) {
      if (service is AdvancedAppService) service.start {} else service.start()
      started += service
    }
    Browser(config.localUrl()).block()
  } finally {
    started.asReversed().forEach { it.shutdown() }
  }
}

/**
 * Renders pages with the client's Node.js bundle, as `run` does. Skipped where Node.js isn't available, except on CI,
 * where it must run; under Gradle it always is, since the test task points SSR_NODE at the Node.js it downloads.
 */
val ServerRenderingTest by testSuite(
  testConfig = if (System.getenv("CI") != null || nodeRuns) TestConfig else TestConfig.disable()
) {
  test("the first response is the page, rendered with the todos") {
    withApp {
      send("POST", TODOS_PATH, """{"title":"Write an example"}""")

      val page = page()
      assert("<span>Write an example</span>" in page)
      assert("0 of 1 done" in page)
      // the state the client in the browser starts from, rather than loading the todos again
      assert("window.KILUA_SSR_STATE" in page)
      assert("""<script type="module" src="/main.bundle.js">""" in page)
    }
  }

  test("each page is rendered with the latest todos") {
    withApp {
      assert("Nothing to do." in page())

      send("POST", TODOS_PATH, """{"title":"Write an example"}""")
      assert("0 of 1 done" in page())

      send("PATCH", "$TODOS_PATH/1", """{"done":true}""")
      val page = page()
      assert("""<input type="checkbox" checked=""><span class="done">Write an example</span>""" in page)
      assert("1 of 1 done" in page)
    }
  }
}
