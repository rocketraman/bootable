import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable

fun main() {
  val api = TodoApi()
  renderComposable(rootElementId = "root") {
    Style(AppStyles)
    TodoApp(api)
  }
}

@Composable
fun TodoApp(api: TodoApi) {
  // null until the first load completes
  var todos by remember { mutableStateOf<List<Todo>?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  // runs a call to the server, showing why it failed if it did
  fun call(block: suspend () -> Unit) {
    scope.launch {
      try {
        block()
        error = null
      } catch (e: Exception) {
        error = e.message ?: "Something went wrong."
      }
    }
  }

  LaunchedEffect(Unit) {
    call { todos = api.all() }
  }

  Div({ classes(AppStyles.container) }) {
    H1 { Text("Todos") }
    P({ classes(AppStyles.muted) }) {
      Text("A Compose HTML client, sharing its models with a Ktor server run by Bootable.")
    }

    NewTodoForm(onAdd = { title ->
      call {
        // read the list after the call returns, so adds that overlap don't drop each other
        val added = api.add(title)
        todos = todos.orEmpty() + added
      }
    })

    error?.let { P({ classes(AppStyles.error) }) { Text(it) } }

    val current = todos
    when {
      current == null -> P({ classes(AppStyles.muted) }) { Text("Loading…") }
      current.isEmpty() -> P({ classes(AppStyles.muted) }) { Text("Nothing to do.") }
      else -> {
        Ul({ classes(AppStyles.list) }) {
          for (todo in current) {
            key(todo.id) {
              TodoItem(
                todo,
                onToggle = { done ->
                  call {
                    val updated = api.update(todo.id, done)
                    todos = todos?.map { if (it.id == updated.id) updated else it }
                  }
                },
                onDelete = {
                  call {
                    api.delete(todo.id)
                    todos = todos?.filter { it.id != todo.id }
                  }
                },
              )
            }
          }
        }
        P({ classes(AppStyles.muted) }) { Text("${current.count { it.done }} of ${current.size} done") }
      }
    }
  }
}

@Composable
fun NewTodoForm(onAdd: (String) -> Unit) {
  var title by remember { mutableStateOf("") }
  // set by a refused submit, so an empty title is only complained about once the user tries to add it
  var refused by remember { mutableStateOf(false) }
  // the same rule the server applies
  val problem = titleProblem(title)

  Form(attrs = {
    classes(AppStyles.form)
    onSubmit { event ->
      event.preventDefault()
      // checked against the current title, which may be newer than this composition's
      if (titleProblem(title) == null) {
        onAdd(title.trim())
        title = ""
        refused = false
      } else {
        refused = true
      }
    }
  }) {
    TextInput(title) {
      classes(AppStyles.input)
      placeholder("What needs doing?")
      // one over the limit, so the shared rule gets to explain it
      maxLength(MAX_TITLE_LENGTH + 1)
      onInput { title = it.value }
    }
    Button({ type(ButtonType.Submit) }) {
      Text("Add")
    }
  }
  if (problem != null && (title.isNotEmpty() || refused)) {
    P({ classes(AppStyles.error) }) { Text(problem) }
  }
}

@Composable
fun TodoItem(todo: Todo, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
  Li({ classes(AppStyles.item) }) {
    Label(attrs = { classes(AppStyles.label) }) {
      CheckboxInput(todo.done) {
        onInput { onToggle(it.value) }
      }
      Span({ if (todo.done) classes(AppStyles.done) }) { Text(todo.title) }
    }
    Button({
      classes(AppStyles.delete)
      attr("aria-label", "Delete ${todo.title}")
      onClick { onDelete() }
    }) {
      Text("✕")
    }
  }
}

object AppStyles : StyleSheet() {
  val container by style {
    maxWidth(36.em)
    property("margin", "2em auto")
    padding(0.px, 1.em)
    fontFamily("system-ui", "sans-serif")
  }

  val muted by style {
    color(Color.gray)
  }

  val error by style {
    color(Color.firebrick)
  }

  val form by style {
    display(DisplayStyle.Flex)
    gap(0.5.em)
  }

  val input by style {
    flex(1)
    padding(0.4.em)
  }

  val list by style {
    property("list-style", "none")
    padding(0.px)
  }

  val item by style {
    display(DisplayStyle.Flex)
    alignItems(AlignItems.Center)
    justifyContent(JustifyContent.SpaceBetween)
    padding(0.4.em, 0.px)
    property("border-bottom", "1px solid #ddd")
  }

  val label by style {
    display(DisplayStyle.Flex)
    alignItems(AlignItems.Center)
    gap(0.5.em)
  }

  val done by style {
    textDecoration("line-through")
    color(Color.gray)
  }

  val delete by style {
    property("border", "none")
    backgroundColor(Color.transparent)
    color(Color.gray)
    cursor("pointer")
  }
}
