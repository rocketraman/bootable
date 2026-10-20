import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Keeps todos in memory, so they last until the server stops. See the `server-ktor-db` example for storing data in a
 * database instead.
 */
class TodoRepository {
  private val ids = AtomicLong()
  private val todos = ConcurrentSkipListMap<Long, Todo>()

  /** Every todo, oldest first. */
  fun all(): List<Todo> = todos.values.toList()

  fun add(title: String): Todo {
    val todo = Todo(ids.incrementAndGet(), title)
    todos[todo.id] = todo
    return todo
  }

  /** Returns the updated todo, or null if there is none with [id]. */
  fun update(id: Long, done: Boolean): Todo? = todos.computeIfPresent(id) { _, todo -> todo.copy(done = done) }

  /** Returns whether there was a todo to delete. */
  fun delete(id: Long): Boolean = todos.remove(id) != null
}
