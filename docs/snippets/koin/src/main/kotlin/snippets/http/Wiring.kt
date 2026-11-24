package snippets.http

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.config.koin.config
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import com.github.rocketraman.bootable.server.http.ktor.koin.ktorHealthModule
import com.github.rocketraman.bootable.server.http.ktor.koin.ktorServerCioEngineModule
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

// tag::wire[]
val serverModule = module {
  includes(ktorHealthModule())  // KtorHealth, over bootableModule()'s HealthLifecycleController, from health.ktor
  config<ServerConfig>("server")
  single<AppService>(named<GreetingServer>()) { GreetingServer(get(), get()) }
  // the engine of the probes' own listener, which ktorHealthModule() adds, if health.ktor.hostPort is set
  includes(ktorServerCioEngineModule())
}

fun appKoin() = koinApplication {
  modules(
    bootableModule(),  // the lifecycle, and its HealthLifecycleController
    hopliteConfigModule(),
    serverModule,
  )
}.koin

fun main() = boot {
  appKoin().get<Bootable>()
}
// end::wire[]
