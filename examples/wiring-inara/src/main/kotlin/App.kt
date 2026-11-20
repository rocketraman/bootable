import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.HopliteConfigModule
import com.github.rocketraman.bootable.config.config
import org.apache.logging.log4j.kotlin.logger
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

data class GreetingConfig(val message: String)

class GreetingService(private val cfg: GreetingConfig) : AppService {
  private val log = logger()
  override fun start() = log.info { "greeting: ${cfg.message}" }
}

// The application's module: its config (boot-config-hoplite:inara) and Bootable's (boot:inara), composed with its own.
@DIModule(composed = true)
interface AppModule : ConfigModule, BootableModule {
  val greetingConfig: GreetingConfig
  @AppServices val greetingService: GreetingService
}

// Inara: KSP generates AppModule(...), which takes each parent, as a built module or a part, plus a binding for each of
// its own members; a missing one doesn't compile. Its bootable starts every @AppServices member of the module.
fun main() = boot {
  AppModule(
    configModule = HopliteConfigModule(),
    bootableModule = bootableModule(),
    greetingConfig = config("greeting"),
    greetingService = Binding.single { GreetingService(greetingConfig) },
  ).bootable
}
