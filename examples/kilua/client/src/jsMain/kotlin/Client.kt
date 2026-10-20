import androidx.compose.runtime.*
import dev.kilua.Application
import dev.kilua.compose.root
import dev.kilua.core.IComponent
import dev.kilua.form.check.checkBox
import dev.kilua.form.form
import dev.kilua.form.text.textRef
import dev.kilua.html.*
import dev.kilua.ssr.getSsrState
import dev.kilua.ssr.ssrRouter
import dev.kilua.startApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import web.events.Event

class App : Application() {
  override fun start() {
    val state = TodoState(TodoApi())
    root("root") {
      // Kilua's router for apps that are also rendered on the server: in Node.js, it renders the page for each path the
      // server's renderer asks for, once the route's action has finished, and adds the state to the page for the browser
      ssrRouter(stateSerializer = { Json.encodeToString(state.todos) }) {
        route("/") {
          view { todoApp(state) }
          action { state.load() }
        }
      }
    }
  }
}

fun main() {
  startApplication(::App)
}

/** The todos, and why the last call to the server failed, if it did. */
class TodoState(private val api: TodoApi) {
  // in the browser, the todos the server rendered the page with; null until loaded
  var todos by mutableStateOf(getSsrState<List<Todo>?>())
  var error by mutableStateOf<String?>(null)

  // so the route's first load doesn't fetch the todos the page already came with
  private var fromPage = todos != null

  suspend fun load() {
    if (fromPage) fromPage = false else call { todos = api.all() }
  }

  suspend fun add(title: String) = call {
    // read the list after the call returns, so adds that overlap don't drop each other
    val added = api.add(title)
    todos = todos.orEmpty() + added
  }

  suspend fun toggle(todo: Todo, done: Boolean) = call {
    val updated = api.update(todo.id, done)
    todos = todos?.map { if (it.id == updated.id) updated else it }
  }

  suspend fun delete(todo: Todo) = call {
    api.delete(todo.id)
    todos = todos?.filter { it.id != todo.id }
  }

  private suspend fun call(block: suspend () -> Unit) {
    try {
      block()
      error = null
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      // Throwable, not Exception: Ktor's JavaScript client reports a failed connection with an Error
      error = e.message ?: "Something went wrong."
    }
  }
}

@Composable
fun IComponent.todoApp(state: TodoState) {
  val scope = rememberCoroutineScope()

  div("container") {
    h1 { +"Todos" }
    p("muted") {
      +"A Kilua client, rendered on the server by Node.js, sharing its models with a Ktor server run by Bootable."
    }

    newTodoForm(onAdd = { title -> scope.launch { state.add(title) } })

    state.error?.let { p("error") { +it } }

    val current = state.todos
    when {
      current == null -> p("muted") { +"Loading…" }
      current.isEmpty() -> p("muted") { +"Nothing to do." }
      else -> {
        ul("list") {
          for (todo in current) {
            key(todo.id) {
              todoItem(
                todo,
                onToggle = { done -> scope.launch { state.toggle(todo, done) } },
                onDelete = { scope.launch { state.delete(todo) } },
              )
            }
          }
        }
        p("muted") { +"${current.count { it.done }} of ${current.size} done" }
      }
    }
  }
}

@Composable
fun IComponent.newTodoForm(onAdd: (String) -> Unit) {
  var title by remember { mutableStateOf("") }
  // set by a refused submit, so an empty title is only complained about once the user tries to add it
  var refused by remember { mutableStateOf(false) }
  // the same rule the server applies
  val problem = titleProblem(title)

  form(className = "form") {
    // Kilua's inputs keep their own value, as the DOM's do, rather than taking it from state, so this reads it as it
    // changes and clears it directly; the length limit is one over, so the shared rule gets to explain it
    val input = textRef(maxlength = MAX_TITLE_LENGTH + 1, placeholder = "What needs doing?", className = "input") {
      onInput { title = value.orEmpty() }
    }
    button("Add", type = ButtonType.Submit)
    onEvent<Event>("submit") { event ->
      event.preventDefault()
      // checked against the current title, which may be newer than this composition's
      if (titleProblem(title) == null) {
        onAdd(title.trim())
        input.value = null
        title = ""
        refused = false
      } else {
        refused = true
      }
    }
  }
  if (problem != null && (title.isNotEmpty() || refused)) {
    p("error") { +problem }
  }
}

@Composable
fun IComponent.todoItem(todo: Todo, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
  li("item") {
    label(className = "label") {
      checkBox(todo.done) {
        // the value sets the checked property, which a server-rendered page doesn't include; the attribute it does
        attribute("checked", if (todo.done) "" else null)
        onChange { onToggle(value) }
      }
      span(if (todo.done) "done" else null) { +todo.title }
    }
    button("✕", className = "delete") {
      ariaLabel("Delete ${todo.title}")
      onClick { onDelete() }
    }
  }
}
