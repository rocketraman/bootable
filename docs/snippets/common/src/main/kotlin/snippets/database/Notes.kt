package snippets.database

import com.github.rocketraman.bootable.db.exposed.withTransaction
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

// tag::table[]
object NoteTable : Table("note") {
  val id = integer("id").autoIncrement()
  val text = text("text")
  override val primaryKey = PrimaryKey(id)
}
// end::table[]

// tag::repository[]
class NoteRepository(private val db: Database) {
  suspend fun add(text: String): Int = db.withTransaction {
    NoteTable.insert { it[NoteTable.text] = text }[NoteTable.id]
  }

  suspend fun count(): Long = db.withTransaction {
    NoteTable.selectAll().count()
  }
}
// end::repository[]
