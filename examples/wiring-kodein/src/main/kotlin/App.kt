import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.bindAppService
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigModule
import org.apache.logging.log4j.kotlin.logger
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.di.singleton

data class GreetingConfig(val message: String)

class GreetingService(private val cfg: GreetingConfig) : AppService {
  private val log = logger()
  override fun start() = log.info { "greeting: ${cfg.message}" }
}

// Kodein: assemble the sets with Kodein's runtime multibindings, then hand the resolved Bootable to boot.
fun main() = boot {
  val di = DI {
    import(bootableModule)      // Bootable + default controllers + the multibinding sets (boot:kodein)
    import(hopliteConfigModule) // binds ConfigBinder (boot-config-hoplite:kodein)
    bindSingleton { instance<ConfigBinder>().config<GreetingConfig>("greeting") }
    bindAppService { singleton { GreetingService(instance()) } }
  }
  di.direct.instance<Bootable>()
}
