package snippets.http

import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.server.http.ktor.KtorHealthModule
import com.github.rocketraman.bootable.server.http.ktor.KtorServerEngineModule
import com.github.rocketraman.bootable.server.http.ktor.ktorHealthModule
import com.github.rocketraman.bootable.server.http.ktor.ktorServerCioEngineModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

// tag::wire[]
@DIModule
interface ServerModule : @DIModule.Required KtorHealthModule {
  val serverConfig: ServerConfig
  @AppServices val greetingServer: GreetingServer
}

fun serverModule() = ServerModulePart(
  serverConfig = config("server"),
  greetingServer = Binding.single { GreetingServer(serverConfig, ktorHealth) },
)

@DIModule(composed = true)
interface AppModule : ConfigModule, BootableModule, KtorServerEngineModule, KtorHealthModule, ServerModule

fun appModule() = AppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),  // the lifecycle, and its HealthLifecycleController
  ktorHealthModule = ktorHealthModule(),  // KtorHealth, over that HealthLifecycleController, from health.ktor
  // the engine of the probes' own listener, which ktorHealthModule adds, if health.ktor.hostPort is set
  ktorServerEngineModule = ktorServerCioEngineModule(),
  serverModule = serverModule(),
)

fun main() = boot {
  appModule().bootable
}
// end::wire[]
