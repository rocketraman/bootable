/** The longest title a todo can have. */
const val MAX_TITLE_LENGTH = 80

data class Todo(val id: Long, val title: String, val done: Boolean = false)

/** Returns why [title] can't be a todo's title, or null if it can. */
fun titleProblem(title: String): String? = when {
  title.isBlank() -> "Enter a title."
  title.length > MAX_TITLE_LENGTH -> "Keep the title to $MAX_TITLE_LENGTH characters or fewer."
  else -> null
}
