package snippets.http

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.bindAppService
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.server.http.ktor.ktorHealthModule
import com.github.rocketraman.bootable.server.http.ktor.ktorServerCioEngineModule
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.di.new
import org.kodein.di.singleton

// tag::wire[]
val serverModule by DI.Module {
  import(ktorHealthModule)  // KtorHealth, over bootableModule's HealthLifecycleController, from health.ktor
  bindSingleton { instance<ConfigBinder>().config<ServerConfig>("server") }
  bindAppService { singleton { new(::GreetingServer) } }
  // the engine of the probes' own listener, which ktorHealthModule adds, if health.ktor.hostPort is set
  import(ktorServerCioEngineModule)
}

fun appDI() = DI {
  import(bootableModule)  // the lifecycle, and its HealthLifecycleController
  import(hopliteConfigModule)
  import(serverModule)
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
// end::wire[]
