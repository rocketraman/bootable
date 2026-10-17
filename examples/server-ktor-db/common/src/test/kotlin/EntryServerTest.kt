import com.github.rocketraman.bootable.db.jdbc.HikariConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively

/**
 * Runs the server's routes against a real SQLite database in a temporary directory, wired as in `main` but with the
 * services started by hand instead of by Bootable.
 */
@OptIn(ExperimentalPathApi::class)
private fun withServer(block: suspend HttpClient.() -> Unit) {
  val dir = createTempDirectory("server-ktor-db")
  val database = JdbcDataSourceService(SqliteConfig(path = dir.resolve("test.db").toString()), HikariConnectionPoolFactory())
  val schema = EntrySchema(database.dataSource)
  try {
    database.start()
    schema.start()
    testApplication {
      val server = EntryServer(ServerConfig("127.0.0.1:0"), EntryRepository(database.dataSource), "test")
      application { with(server) { module() } }
      client.block()
    }
  } finally {
    database.shutdown()
    dir.deleteRecursively()
  }
}

val EntryServerTest by testSuite {
  testSuite("the index page") {
    test("says which bindings wired the server") {
      withServer {
        assert("run by Bootable, using test bindings." in get("/").bodyAsText())
      }
    }

    test("says when there are no entries") {
      withServer {
        val response = get("/")
        assert(response.status == HttpStatusCode.OK)
        assert(response.contentType()?.match(ContentType.Text.Html) == true)
        assert("None yet." in response.bodyAsText())
      }
    }

    test("links each entry, escaping its name and value") {
      withServer {
        put("/entries/my%20key") { setBody("<b>bold</b>") }

        val body = get("/").bodyAsText()
        assert("""href="/entries/my%20key"""" in body)
        assert("""data-name="my key"""" in body)
        assert("""data-value="&lt;b&gt;bold&lt;/b&gt;"""" in body)
        assert(">my key</a> = &lt;b&gt;bold&lt;/b&gt;" in body)
      }
    }

    test("has a button for each operation") {
      withServer {
        val body = get("/").bodyAsText()
        for (operation in listOf("GET", "PUT", "DELETE", "LIST")) {
          assert("""data-method="$operation"""" in body)
        }
        assert("""id="result"""" in body)
      }
    }
  }

  test("a stored entry can be read back") {
    withServer {
      assert(put("/entries/greeting") { setBody("hello") }.status == HttpStatusCode.NoContent)

      val response = get("/entries/greeting")
      assert(response.status == HttpStatusCode.OK)
      assert(response.bodyAsText() == "hello")
    }
  }

  test("storing an existing entry replaces its value") {
    withServer {
      put("/entries/greeting") { setBody("hello") }
      put("/entries/greeting") { setBody("hi") }

      assert(get("/entries/greeting").bodyAsText() == "hi")
    }
  }

  test("all entries are listed in name order") {
    withServer {
      put("/entries/b") { setBody("2") }
      put("/entries/a") { setBody("1") }

      assert(get("/entries").bodyAsText() == "a=1\nb=2\n")
    }
  }

  test("a missing entry is not found") {
    withServer {
      assert(get("/entries/missing").status == HttpStatusCode.NotFound)
      assert(delete("/entries/missing").status == HttpStatusCode.NotFound)
    }
  }

  test("a deleted entry is gone") {
    withServer {
      put("/entries/greeting") { setBody("hello") }

      assert(delete("/entries/greeting").status == HttpStatusCode.NoContent)
      assert(get("/entries/greeting").status == HttpStatusCode.NotFound)
    }
  }
}
