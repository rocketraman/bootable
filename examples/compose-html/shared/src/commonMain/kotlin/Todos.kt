import kotlinx.serialization.Serializable

/** The API's root path, which the server routes and the client calls. */
const val TODOS_PATH = "/api/todos"

/** The longest title a todo can have. */
const val MAX_TITLE_LENGTH = 80

/** A stored todo, as the server returns it. */
@Serializable
data class Todo(val id: Long, val title: String, val done: Boolean = false)

/** The body of `POST /api/todos`, which adds a todo. */
@Serializable
data class NewTodo(val title: String)

/** The body of `PATCH /api/todos/{id}`, which marks a todo done or not done. */
@Serializable
data class TodoUpdate(val done: Boolean)

/**
 * Returns why [title] can't be a todo's title, or null if it can.
 *
 * The client checks this as the user types, and the server checks it again on every request, so the two can't disagree.
 */
fun titleProblem(title: String): String? = when {
  title.isBlank() -> "Enter a title."
  title.length > MAX_TITLE_LENGTH -> "Keep the title to $MAX_TITLE_LENGTH characters or fewer."
  else -> null
}
