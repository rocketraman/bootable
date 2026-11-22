package snippets.background

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.boot
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph

sealed interface BackgroundScope

// The -metro modules contribute the loops' PeriodicServices, the leader election, the scheduler, and the database's
// bindings; this application's own bindings, in Periodic.kt, Leader.kt, and Scheduler.kt, add its loop, its HTTP
// clients' engine, and its tasks.
@DependencyGraph(BackgroundScope::class, additionalScopes = [BootScope::class])
interface BackgroundGraph : BootableGraph

fun main() = boot {
  createGraph<BackgroundGraph>().bootable
}
