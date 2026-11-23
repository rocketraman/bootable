import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import org.apache.logging.log4j.kotlin.logger
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

data class GreetingConfig(val message: String)

class GreetingService(private val cfg: GreetingConfig) : AppService {
  private val log = logger()
  override suspend fun start() = log.info { "greeting: ${cfg.message}" }
}

// Koin has no set multibindings: Bootable runs every AppService definition, each with a qualifier of its own.
val appModule = module {
  single { get<ConfigBinder>().config<GreetingConfig>("greeting") }
  single<AppService>(named("greeting")) { GreetingService(get()) }
}

// Koin: collect the services with Koin's getAll, then hand the resolved Bootable to boot.
fun main() = boot {
  koinApplication {
    modules(
      bootableModule(),      // Bootable + default controllers, over every AppService and LifecycleController (boot:koin)
      hopliteConfigModule(), // defines ConfigBinder (boot-config-hoplite:koin)
      appModule,
    )
  }.koin.get<Bootable>()
}
