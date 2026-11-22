package snippets.environment

import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.env.EnvironmentModule
import com.github.rocketraman.bootable.env.environmentModule
import org.kodein.di.inara.DIModule

// tag::wire[]
@DIModule(composed = true)
interface AppModule : ConfigModule, BootableModule, EnvironmentModule  // ... and the application's services

fun appModule() = AppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  environmentModule = environmentModule(),  // sets environment from env.environment, before services start
)

fun main() = boot {
  appModule().bootable
}
// end::wire[]
