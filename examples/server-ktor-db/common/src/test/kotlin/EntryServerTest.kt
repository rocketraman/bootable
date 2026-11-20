import com.github.rocketraman.bootable.db.exposed.ExposedDatabaseService
import com.github.rocketraman.bootable.db.exposed.SchemaVerificationService
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.jdbc.HikariConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.JdbcHealthCheck
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import com.github.rocketraman.bootable.boot.Health
import com.github.rocketraman.bootable.boot.ServiceContext
import com.github.rocketraman.bootable.boot.StartupTimeline
import com.github.rocketraman.bootable.server.http.ktor.KtorHealth
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.telemetry.otel.MetricsConfig
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.PrometheusTelemetry
import com.github.rocketraman.bootable.telemetry.otel.TelemetryConfig
import com.github.rocketraman.bootable.test.captureLogs
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.time.TimeSource
import org.apache.logging.log4j.Level

/**
 * Runs the server's routes against a real SQLite database in a temporary directory, wired as in `main` but with the
 * services started by hand instead of by Bootable.
 */
@OptIn(ExperimentalPathApi::class)
private suspend fun withServer(block: suspend HttpClient.(health: Health) -> Unit) {
  val dir = createTempDirectory("server-ktor-db")
  val database = JdbcDataSourceService(SqliteConfig(path = dir.resolve("test.db").toString()), HikariConnectionPoolFactory())
  val exposed = ExposedDatabaseService(database.dataSource)
  val db = exposed.database
  val health = Health(setOf(JdbcHealthCheck(database.dataSource)))
  try {
    database.start()
    FlywayMigrationService(database.dataSource).start()
    SchemaVerificationService(db, setOf(EntryTable)).start()
    testApplication {
      val prometheus = PrometheusTelemetry()
      val openTelemetry = OpenTelemetryService(
        TelemetryConfig(metrics = MetricsConfig(runtime = false), registerGlobal = false),
        listOf(prometheus),
      ).openTelemetry
      // the relay calls the test application itself, as the running server calls itself back
      val relay = createClient { }
      val server = EntryServer(ServerConfig("127.0.0.1:0"), EntryRepository(db), KtorTelemetry(openTelemetry, setOf(prometheus)), KtorHealth(health), relay, "test")
      application { with(server) { module() } }
      client.block(health)
    }
  } finally {
    exposed.shutdown()
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

  test("a stored entry can be read back, and is logged") {
    withServer {
      val logs = captureLogs("EntryServer") {
        assert(put("/entries/greeting") { setBody("hello") }.status == HttpStatusCode.NoContent)
      }
      assert(logs.messages(Level.INFO) == listOf("Stored entry greeting"))

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

  test("the metrics count stored entries, and are served for Prometheus to scrape") {
    withServer {
      put("/entries/a") { setBody("1") }
      put("/entries/b") { setBody("2") }

      val response = get("/metrics")
      assert(response.status == HttpStatusCode.OK)
      assert(response.bodyAsText().lines().any { it.startsWith("entries_stored_total{") && it.endsWith(" 2.0") })
    }
  }

  test("is live, and ready once the application is running and the database answers") {
    withServer { health ->
      assert(get("/health/live").status == HttpStatusCode.OK)
      assert(get("/health/ready").status == HttpStatusCode.ServiceUnavailable)

      // the application is running once its start completes, as Bootable runs it
      health.aroundAppStart(ServiceContext(emptySet(), emptyList(), {}, {}), StartupTimeline(TimeSource.Monotonic.markNow())) {}
      val ready = get("/health/ready")
      assert(ready.status == HttpStatusCode.OK)
      assert(ready.contentType()?.match(ContentType.Application.Json) == true)
      assert(ready.bodyAsText() == """{"status":"UP","state":"Running","checks":{"jdbc":{"status":"UP"}}}""")
    }
  }

  test("the relay gets an entry by calling the server back, and passes on a missing one's 404") {
    withServer {
      put("/entries/greeting") { setBody("hello") }

      assert(get("/relay/greeting").bodyAsText() == "hello")
      assert(get("/relay/missing").status == HttpStatusCode.NotFound)
    }
  }
}
