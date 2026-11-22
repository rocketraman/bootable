package snippets.background

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.exposed.withTransaction
import com.github.rocketraman.bootable.leader.LeaderElection
import com.github.rocketraman.bootable.periodic.PeriodicAppService
import com.github.rocketraman.bootable.periodic.PeriodicGate
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.update

// tag::store[]
// one row for each loop, which the schema's migration inserts
object CursorTable : Table("cursor") {
  val name = varchar("name", 50)
  val position = long("position")       // the last entry the loop has processed
  val fence = long("fence").default(0)  // the highest fencing token that has written the row
  override val primaryKey = PrimaryKey(name)
}

class Cursors(private val db: Database) {
  /** Moves cursor [name] to [position], unless a later lease term has written it; returns whether it did. */
  suspend fun advance(name: String, position: Long, token: Long): Boolean = db.withTransaction {
    val updated = CursorTable.update({ (CursorTable.name eq name) and (CursorTable.fence lessEq token) }) {
      it[CursorTable.position] = position
      it[CursorTable.fence] = token
    }
    updated == 1
  }
}
// end::store[]

fun fencedLoop(
  binder: ConfigBinder,
  leaderElection: LeaderElection,
  cursors: Cursors,
  processNewEntries: suspend () -> Long,
) =
  // tag::loop[]
  PeriodicAppService("digest", binder.config("digest.loop"), PeriodicGate.leader(leaderElection)) {
    val token = leaderElection.fencingToken ?: return@PeriodicAppService  // null: this instance doesn't lead
    val position = processNewEntries()
    cursors.advance("digest", position, token)  // false: a later lease term has written it, and this one is over
  }
  // end::loop[]
