package snippets.configuration

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.sksamuel.hoplite.Secret
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

data class GreetingConfig(val message: String, val apiKey: Secret, val retries: Int = 3)

class GreetingService(private val config: GreetingConfig) : AppService {
  override suspend fun start() = println(config.message)
}

// tag::wire[]
@DIModule(composed = true)
interface AppModule : ConfigModule, BootableModule {
  val greetingConfig: GreetingConfig
  @AppServices val greetingService: GreetingService
}

fun appModule() = AppModule(
  configModule = hopliteConfigModule(),  // provides the ConfigBinder
  bootableModule = bootableModule(),
  greetingConfig = config("greeting"),   // decoded once, from the ConfigBinder
  greetingService = Binding.single { GreetingService(greetingConfig) },
)

fun main() = boot {
  appModule().bootable
}
// end::wire[]

// tag::custom[]
// passed as the configModule, in place of hopliteConfigModule()
val customConfigModule = ConfigModule(
  configBinder = Binding.single { hopliteConfigBinder(report = false, strict = true) },
)
// end::custom[]
