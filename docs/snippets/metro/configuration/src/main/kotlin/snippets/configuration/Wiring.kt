package snippets.configuration

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.ConfigBinderProvider
import com.github.rocketraman.bootable.config.config
import com.sksamuel.hoplite.Secret
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph

sealed interface ConfigurationScope

data class GreetingConfig(val message: String, val apiKey: Secret, val retries: Int = 3)

@ContributesIntoSet(ConfigurationScope::class)
@SingleIn(ConfigurationScope::class)
@Inject
class GreetingService(private val config: GreetingConfig) : AppService {
  override suspend fun start() = println(config.message)
}

// tag::wire[]
// boot-config-hoplite-metro contributes the ConfigBinder; each config binding is a type and a key
@ContributesTo(ConfigurationScope::class)
interface GreetingConfigBindings : ConfigBinderProvider {
  @Provides fun greetingConfig(): GreetingConfig = config("greeting")
}

@DependencyGraph(ConfigurationScope::class, additionalScopes = [BootScope::class])
interface ConfigurationGraph : BootableGraph

fun main() = boot {
  createGraph<ConfigurationGraph>().bootable
}
// end::wire[]
