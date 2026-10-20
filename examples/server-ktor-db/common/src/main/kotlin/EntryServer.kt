import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.server.http.ktor.CioKtorService
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
 */
class EntryServer(
  config: ServerConfig,
  private val entries: EntryRepository,
  private val bindings: String,
) : CioKtorService("entries", config) {
  private val logger = logger()

  override fun Application.module() {
    routing {
      get("/") {
        val all = entries.all()
        call.respondHtml(HttpStatusCode.OK) { index(all, bindings) }
      }

      route("/entries") {
        get {
          val all = entries.all()
          call.respondText(all.entries.joinToString("") { (name, value) -> "$name=$value\n" })
        }

        get("/{name}") {
          val name = call.parameters["name"]!!
          val value = entries.get(name)
          if (value == null) call.respond(HttpStatusCode.NotFound) else call.respondText(value)
        }

        put("/{name}") {
          val name = call.parameters["name"]!!
          val value = call.receiveText()
          entries.put(name, value)
          logger.info { "Stored entry $name" }
          call.respond(HttpStatusCode.NoContent)
        }

        delete("/{name}") {
          val name = call.parameters["name"]!!
          val deleted = entries.delete(name)
          call.respond(if (deleted) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }
      }
    }
  }
}
