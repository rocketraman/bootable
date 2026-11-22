package snippets.database

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.boot
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database

sealed interface AppScope

// tag::wire[]
// The -metro driver, pool, Flyway, and Exposed modules contribute the database's bindings; the application adds its
// tables and what uses the Database.
@ContributesTo(AppScope::class)
interface NoteBindings {
  @Provides @IntoSet fun noteTable(): Table = NoteTable  // verified against the schema

  @Provides @SingleIn(AppScope::class)
  fun noteRepository(db: Database): NoteRepository = NoteRepository(db)
  // ... and the services that use NoteRepository
}

@DependencyGraph(AppScope::class, additionalScopes = [BootScope::class])
interface AppGraph : BootableGraph

fun main() = boot {
  createGraph<AppGraph>().bootable
}
// end::wire[]
