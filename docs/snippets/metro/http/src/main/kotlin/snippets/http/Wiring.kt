package snippets.http

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.boot
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph

sealed interface HttpScope

// tag::graph[]
// The -metro modules contribute KtorHealth, the KtorClientService, and the telemetry; the application contributes
// its server, its client engine, and what uses them, to HttpScope.
@DependencyGraph(HttpScope::class, additionalScopes = [BootScope::class])
interface HttpGraph : BootableGraph

fun main() = boot {
  createGraph<HttpGraph>().bootable
}
// end::graph[]
