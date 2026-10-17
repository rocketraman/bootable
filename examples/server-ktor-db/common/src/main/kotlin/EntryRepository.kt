import com.github.rocketraman.bootable.db.exposed.withTransaction
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * The `entry` table, as created by the Flyway migration in `db/migration`. It is registered for schema verification,
 * so the application fails to start if the migrated schema doesn't match this definition.
 */
object EntryTable : Table("entry") {
  val name = text("name")
  val value = text("value")
  override val primaryKey = PrimaryKey(name)
}

/**
 * Reads and writes entries with Exposed, off the caller's thread. Each call is its own transaction, unless the caller
 * runs it inside a `withTransaction` of its own, which it then joins.
 */
class EntryRepository(private val db: Database) {
  suspend fun all(): Map<String, String> = db.withTransaction {
    EntryTable.selectAll().orderBy(EntryTable.name to SortOrder.ASC)
      .associate { it[EntryTable.name] to it[EntryTable.value] }
  }

  suspend fun get(name: String): String? = db.withTransaction {
    EntryTable.selectAll().where { EntryTable.name eq name }.singleOrNull()?.get(EntryTable.value)
  }

  suspend fun put(name: String, value: String) {
    db.withTransaction {
      EntryTable.upsert {
        it[EntryTable.name] = name
        it[EntryTable.value] = value
      }
    }
  }

  /** Returns whether there was an entry to delete. */
  suspend fun delete(name: String): Boolean = db.withTransaction {
    EntryTable.deleteWhere { EntryTable.name eq name } > 0
  }
}
