import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.boot
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

// The application's module: Bootable's (boot:inara), which includes its config, composed with its own.
@DIModule(composed = true)
interface AppModule : BootableModule {
  val greetingConfig: GreetingConfig
  val greetingService: GreetingService
}

// Inara: KSP generates AppModule(...), which takes the built parent modules plus a binding for each of its own
// members; a missing one doesn't compile. There are no multibindings, so the app adds its own services to Bootable's.
fun main() = boot {
  val bootable = BootableModule(HopliteConfigModule())
  val app = AppModule(
    bootableModule = bootable,
    greetingConfig = bootable.config("greeting"),
    greetingService = Binding.single { GreetingService(greetingConfig) },
  )
  app.addAppService { app.greetingService }
  app.bootable
}
