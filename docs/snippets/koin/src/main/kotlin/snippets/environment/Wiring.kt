package snippets.environment

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import com.github.rocketraman.bootable.env.koin.environmentModule
import org.koin.dsl.koinApplication

// tag::wire[]
fun appKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    environmentModule(),  // sets environment from env.environment, before services start
    // ... the application's services
  )
}.koin

fun main() = boot {
  appKoin().get<Bootable>()
}
// end::wire[]
