package snippets.environment

import com.github.rocketraman.bootable.boot.BootableDefaultBindings
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.config.HopliteConfigBindings
import com.github.rocketraman.bootable.env.EnvironmentBindings
import com.github.rocketraman.bootable.env.EnvironmentConfigBindings
import dev.zacsweers.metro.DependencyGraph

sealed interface EnvironmentScope

// tag::explicit[]
@DependencyGraph(
  EnvironmentScope::class,
  bindingContainers = [
    BootableDefaultBindings::class,
    HopliteConfigBindings::class,
    EnvironmentConfigBindings::class,  // EnvironmentConfig, from env
    EnvironmentBindings::class,        // the initializer, in the lifecycle controllers
  ],
)
interface EnvironmentGraph : BootableGraph
// end::explicit[]
