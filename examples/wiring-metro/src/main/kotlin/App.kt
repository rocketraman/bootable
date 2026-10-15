import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.ConfigBinderProvider
import com.github.rocketraman.bootable.config.config
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import org.apache.logging.log4j.kotlin.logger

/** This application's own DI scope — Bootable declares and uses its own [BootScope]. */
sealed interface AppScope

data class GreetingConfig(val message: String)

@ContributesIntoSet(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class GreetingService(private val cfg: GreetingConfig) : AppService {
  private val log = logger()
  override fun start() = log.info { "greeting: ${cfg.message}" }
}

// config bindings are just a type and a key (ConfigBinderProvider supplies the binder as a graph accessor)
@ContributesTo(AppScope::class)
interface AppModule : ConfigBinderProvider {
  @Provides fun greetingConfig(): GreetingConfig = config("greeting")
}

@DependencyGraph(AppScope::class, additionalScopes = [BootScope::class])
interface AppGraph : BootableGraph

// Metro: the compiler assembles the graph; boot runs it.
fun main() = boot {
  createGraph<AppGraph>().bootable
}
