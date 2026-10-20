import io.ktor.htmx.*
import io.ktor.htmx.html.*
import kotlinx.html.*

// the ids of the elements that fragments replace
const val FORM_ID = "add-form"
const val LIST_ID = "todo-list"
const val SUMMARY_ID = "summary"

/**
 * htmx doesn't swap 4xx responses by default, but the server refuses a title with a 422 and the form to show instead,
 * so swap those too. Setting `responseHandling` replaces htmx's defaults, so this repeats the rest of them.
 */
private const val HTMX_CONFIG = """{"responseHandling": [
  {"code": "204", "swap": false},
  {"code": "[23]..", "swap": true},
  {"code": "422", "swap": true},
  {"code": "[45]..", "swap": false, "error": true},
  {"code": "...", "swap": false}
]}"""

/** The whole page, as a plain `GET /` gets it: rendered on the server, so it shows before any script runs. */
fun HTML.todoPage(todos: List<Todo>) {
  lang = "en"
  head {
    meta(charset = "UTF-8")
    meta(name = "viewport", content = "width=device-width, initial-scale=1")
    meta(name = "htmx-config", content = HTMX_CONFIG)
    title("Todos")
    link(rel = "stylesheet", href = "/assets/app.css")
    script(src = "/assets/htmx.min.js") {}
  }
  body {
    div("container") {
      h1 { +"Todos" }
      p("muted") {
        +"Rendered on the server with kotlinx.html, and made interactive with htmx, by a Ktor server run by Bootable."
      }
      // the fragments below are the same ones the routes send htmx, so the page and its updates can't disagree
      consumer.addForm()
      ul("list") {
        id = LIST_ID
        for (todo in todos) consumer.todoItem(todo)
      }
      consumer.summary(todos)
    }
  }
}

/** The form for a new todo, showing [title] and why it was refused, if it was. */
fun <T> TagConsumer<T>.addForm(title: String = "", problem: String? = null, oob: Boolean = false): T = div {
  id = FORM_ID
  if (oob) attributes.hx { swapOob = "true" }
  form(classes = "add") {
    // added todos go at the end of the list
    attributes.hx {
      post = "/todos"
      target = "#$LIST_ID"
      swap = HxSwap.beforeEnd
    }
    textInput(name = "title") {
      value = title
      placeholder = "What needs doing?"
      // one over the limit, so the server gets to explain it
      maxLength = "${MAX_TITLE_LENGTH + 1}"
      autoFocus = true
    }
    button(type = ButtonType.submit) { +"Add" }
  }
  if (problem != null) p("error") { +problem }
}

/** A todo, which replaces itself when toggled, and removes itself when deleted. */
fun <T> TagConsumer<T>.todoItem(todo: Todo): T = li("item") {
  id = "todo-${todo.id}"
  label("label") {
    checkBoxInput {
      checked = todo.done
      attributes.hx {
        patch = "/todos/${todo.id}"
        vals = """{"done": ${!todo.done}}"""
        target = "closest li"
        swap = HxSwap.outerHtml
      }
    }
    span { if (todo.done) classes = setOf("done"); +todo.title }
  }
  button(classes = "delete") {
    attributes["aria-label"] = "Delete ${todo.title}"
    attributes.hx {
      delete = "/todos/${todo.id}"
      target = "closest li"
      swap = HxSwap.delete
    }
    +"✕"
  }
}

/** How many of [todos] are done. */
fun <T> TagConsumer<T>.summary(todos: List<Todo>, oob: Boolean = false): T = p("muted") {
  id = SUMMARY_ID
  if (oob) attributes.hx { swapOob = "true" }
  +if (todos.isEmpty()) "Nothing to do." else "${todos.count { it.done }} of ${todos.size} done"
}
