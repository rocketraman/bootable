package snippets.configuration

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableDefaultBindings
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

// tag::custom[]
@BindingContainer
object CustomConfigBinder {
  @Provides @SingleIn(BootScope::class)
  fun configBinder(): ConfigBinder = hopliteConfigBinder(report = false, strict = true)
}

// Bootable's bindings named, not discovered, so HopliteConfigBindings' default ConfigBinder isn't in the graph
@DependencyGraph(ConfigurationScope::class, bindingContainers = [BootableDefaultBindings::class, CustomConfigBinder::class])
interface CustomConfigGraph : BootableGraph
// end::custom[]
