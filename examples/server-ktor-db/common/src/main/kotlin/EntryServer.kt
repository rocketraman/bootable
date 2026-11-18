import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.server.http.ktor.CioKtorService
import com.github.rocketraman.bootable.server.http.ktor.KtorHealth
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.server.http.ktor.installHealth
import com.github.rocketraman.bootable.server.http.ktor.installTelemetry
import com.github.rocketraman.bootable.telemetry.otel.withSpan
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import org.apache.logging.log4j.kotlin.logger

data class ServerConfig(override val hostPort: String) : HostPort

/**
 * The index page: a form to run each operation against the API, what the API offers, and the entries stored so far.
 * The form calls the same endpoints `curl` would, with `fetch`, since an HTML form can only send GET and POST.
 */
fun HTML.index(entries: Map<String, String>, bindings: String) {
  head {
    title("Entries")
  }
  body {
    h1 { +"Entries" }
    p { +"A key/value store in SQLite, served by Ktor and run by Bootable, using $bindings bindings." }

    h2 { +"Try it" }
    form {
      id = "try"
      p {
        label { +"Name "; textInput(name = "name") }
      }
      p {
        label { +"Value "; textArea(rows = "3", cols = "40") { name = "value" } }
      }
      p {
        for ((method, label) in listOf("GET" to "Get", "PUT" to "Put", "DELETE" to "Delete", "LIST" to "List all")) {
          button(type = ButtonType.button) {
            attributes["data-method"] = method
            +label
          }
          +" "
        }
      }
    }
    pre { id = "result" }

    h2 { +"API" }
    ul {
      li { code { +"GET /entries" }; +" lists every entry, one name=value per line" }
      li { code { +"GET /entries/{name}" }; +" returns an entry's value" }
      li { code { +"PUT /entries/{name}" }; +" stores the request body as the value" }
      li { code { +"DELETE /entries/{name}" }; +" removes an entry" }
      li { code { +"GET /metrics" }; +" the application's metrics, for Prometheus to scrape" }
      li { code { +"GET /health/live" }; +" liveness: 200 until the application has stopped" }
      li { code { +"GET /health/ready" }; +" readiness: 200 while the application is running and its database answers, 503 otherwise" }
    }
    p {
      +"For example: "
      code { +"curl -X PUT --data 'hello' http://localhost:8001/entries/greeting" }
    }

    h2 { +"Stored entries" }
    div {
      id = "entries"
      if (entries.isEmpty()) {
        p { +"None yet." }
      } else {
        p { +"Click a name to load it into the form." }
        ul {
          for ((name, value) in entries) {
            li {
              a(href = "/entries/${name.encodeURLPathPart()}") {
                attributes["data-name"] = name
                attributes["data-value"] = value
                +name
              }
              +" = $value"
            }
          }
        }
      }
    }

    script {
      unsafe { raw(INDEX_SCRIPT) }
    }
  }
}

// Plain JavaScript; no `${` anywhere, since this is a Kotlin string template.
private const val INDEX_SCRIPT = """
const form = document.getElementById('try');
const result = document.getElementById('result');

async function run(operation) {
  const name = form.elements.name.value.trim();
  if (operation !== 'LIST' && !name) {
    result.textContent = 'Enter a name first.';
    return;
  }
  const path = operation === 'LIST' ? '/entries' : '/entries/' + encodeURIComponent(name);
  const method = operation === 'LIST' ? 'GET' : operation;
  const response = await fetch(path, { method: method, body: method === 'PUT' ? form.elements.value.value : undefined });
  const body = await response.text();
  result.textContent = method + ' ' + path + '\n' + response.status + ' ' + response.statusText + (body ? '\n\n' + body : '');
  if (method === 'PUT' || method === 'DELETE') await refreshEntries();
}

// re-render the stored entries from the server's own page, so there is only one renderer
async function refreshEntries() {
  const page = new DOMParser().parseFromString(await (await fetch('/')).text(), 'text/html');
  document.getElementById('entries').replaceWith(page.getElementById('entries'));
}

form.addEventListener('click', event => {
  const operation = event.target.dataset.method;
  if (operation) run(operation);
});
form.addEventListener('submit', event => {
  event.preventDefault();
  run('PUT');
});
document.addEventListener('click', event => {
  const link = event.target.closest('#entries a');
  if (!link) return;
  event.preventDefault();
  form.elements.name.value = link.dataset.name;
  form.elements.value.value = link.dataset.value;
});
"""

/**
 * A plain-text key/value store over HTTP, with an HTML index page at `/`:
 *
 * - `GET /entries` lists every entry, one `name=value` per line
 * - `GET /entries/{name}` returns the value, or 404
 * - `PUT /entries/{name}` stores the request body as the value
 * - `DELETE /entries/{name}` removes the entry, or 404
 *
 * [telemetry] traces and measures every request, and serves the Prometheus scrape at `/metrics`, unless
 * `telemetry.ktor.hostPort` puts it on a port of its own. Each database operation is a span of its own, within the
 * request's, and the server counts the entries it stores, as `entries.stored`.
 *
 * [health] serves liveness at `/health/live` and readiness at `/health/ready`, from Bootable's lifecycle and the
 * database's health check, unless `health.ktor.hostPort` puts them on a port of their own.
 */
class EntryServer(
  config: ServerConfig,
  private val entries: EntryRepository,
  private val telemetry: KtorTelemetry,
  private val health: KtorHealth,
  private val bindings: String,
) : CioKtorService("entries", config) {
  private val logger = logger()
  private val tracer = telemetry.openTelemetry.getTracer("entries")
  private val stored = telemetry.openTelemetry.getMeter("entries").counterBuilder("entries.stored")
    .setDescription("Entries stored, new or replaced")
    .build()

  override fun Application.module() {
    installTelemetry(telemetry)
    installHealth(health)
    routing {
      get("/") {
        val all = tracer.withSpan("entries.all") { entries.all() }
        call.respondHtml(HttpStatusCode.OK) { index(all, bindings) }
      }

      route("/entries") {
        get {
          val all = tracer.withSpan("entries.all") { entries.all() }
          call.respondText(all.entries.joinToString("") { (name, value) -> "$name=$value\n" })
        }

        get("/{name}") {
          val name = call.parameters["name"]!!
          val value = tracer.withSpan("entries.get") { entries.get(name) }
          if (value == null) call.respond(HttpStatusCode.NotFound) else call.respondText(value)
        }

        put("/{name}") {
          val name = call.parameters["name"]!!
          val value = call.receiveText()
          tracer.withSpan("entries.put") { entries.put(name, value) }
          stored.add(1)
          logger.info { "Stored entry $name" }
          call.respond(HttpStatusCode.NoContent)
        }

        delete("/{name}") {
          val name = call.parameters["name"]!!
          val deleted = tracer.withSpan("entries.delete") { entries.delete(name) }
          call.respond(if (deleted) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }
      }
    }
  }
}
