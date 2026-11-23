package snippets.configuration

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import com.sksamuel.hoplite.Secret
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

data class GreetingConfig(val message: String, val apiKey: Secret, val retries: Int = 3)

class GreetingService(private val config: GreetingConfig) : AppService {
  override suspend fun start() = println(config.message)
}

// tag::wire[]
fun appKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),  // defines the ConfigBinder
    module {
      single { get<ConfigBinder>().config<GreetingConfig>("greeting") }
      single<AppService>(named<GreetingService>()) { GreetingService(get()) }
    },
  )
}.koin

fun main() = boot {
  appKoin().get<Bootable>()
}
// end::wire[]

// tag::custom[]
fun customKoin() = koinApplication {
  modules(
    bootableModule(),
    // in place of hopliteConfigModule()
    module { single<ConfigBinder> { hopliteConfigBinder(report = false, strict = true) } },
  )
}.koin
// end::custom[]
