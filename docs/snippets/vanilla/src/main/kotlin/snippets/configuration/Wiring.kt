package snippets.configuration

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.sksamuel.hoplite.PropertySource

class GreetingService(private val config: GreetingConfig) : AppService {
  override suspend fun start() = println(config.message)
}

// tag::wire[]
fun main() = boot {
  val binder = hopliteConfigBinder()
  Bootable(
    setOf(GreetingService(binder.config<GreetingConfig>("greeting"))),
  )
}
// end::wire[]

// tag::custom[]
fun customBinder(): ConfigBinder = hopliteConfigBinder(
  // above application.conf, below everything else
  withConfigStage2 = { addPropertySource(PropertySource.file(java.io.File("/etc/greeter/greeter.conf"), optional = true)) },
  report = false,  // no config report at start
  strict = true,   // fail on a key in a section that its config class has no property for
)
// end::custom[]
