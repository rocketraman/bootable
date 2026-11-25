package snippets.configuration

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.bindAppService
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.bindConfig
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.sksamuel.hoplite.Secret
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.di.new
import org.kodein.di.singleton

data class GreetingConfig(val message: String, val apiKey: Secret, val retries: Int = 3)

class GreetingService(private val config: GreetingConfig) : AppService {
  override suspend fun start() = println(config.message)
}

// tag::wire[]
fun appDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)  // binds the ConfigBinder
  bindConfig<GreetingConfig>("greeting")
  bindAppService { singleton { new(::GreetingService) } }
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
// end::wire[]

// tag::custom[]
fun customDI() = DI {
  import(bootableModule)
  // in place of hopliteConfigModule
  bindSingleton<ConfigBinder> { hopliteConfigBinder(report = false, strict = true) }
}
// end::custom[]
