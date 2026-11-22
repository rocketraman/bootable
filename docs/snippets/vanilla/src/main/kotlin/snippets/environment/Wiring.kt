package snippets.environment

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.defaultLifecycleControllers
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.env.environmentInitializer

// tag::wire[]
fun main() = boot {
  val binder = hopliteConfigBinder()
  Bootable(
    appServices = setOf(/* ... the application's services */),
    lifecycleControllers = defaultLifecycleControllers() + environmentInitializer(binder),
  )
}
// end::wire[]
