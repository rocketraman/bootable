import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import org.apache.logging.log4j.kotlin.logger

data class GreetingConfig(val message: String)

class GreetingService(private val cfg: GreetingConfig) : AppService {
  private val log = logger()
  override fun start() = log.info { "greeting: ${cfg.message}" }
}

// Vanilla: no DI framework at all. Load config, construct the services, hand them to boot.
fun main() = boot {
  val binder = hopliteConfigBinder()
  Bootable(
    appServices = setOf(GreetingService(binder.config("greeting"))),
    // custom lifecycle controllers can be injected here
  )
}
