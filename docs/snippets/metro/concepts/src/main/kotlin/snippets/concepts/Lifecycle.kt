package snippets.concepts

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.server.http.ktor.CioKtorService
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

sealed interface AppScope

class HelloServer(config: HostPort) : CioKtorService("hello", config) {
  override fun Application.module() {
    routing {
      get("/") { call.respondText("hello") }
    }
  }
}

// tag::wire[]
@ContributesTo(AppScope::class)
interface HelloBindings {
  @Provides @IntoSet @SingleIn(AppScope::class)
  fun server(): AppService = HelloServer(HostPort(port = 8000))
}

// BootableGraph brings Bootable, its default controllers, and the sets to contribute into
@DependencyGraph(AppScope::class, additionalScopes = [BootScope::class])
interface AppGraph : BootableGraph

fun main() = boot {
  createGraph<AppGraph>().bootable
}
// end::wire[]
