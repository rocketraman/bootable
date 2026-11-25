package snippets.background

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.config.koin.config
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import com.github.rocketraman.bootable.periodic.PeriodicConfig
import com.github.rocketraman.bootable.periodic.PeriodicServices
import com.github.rocketraman.bootable.periodic.koin.periodicModule
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

// tag::wire[]
fun periodicKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    periodicModule(),  // PeriodicServices, gated on the LeaderElection definition, if there is one
    module {
      single { Notifications() }
      // each loop has a PeriodicConfig of its own, so qualify it
      config<PeriodicConfig>("notifications.loop", named("notifications"))
      single<AppService>(named("notifications")) {
        val notifications = get<Notifications>()
        get<PeriodicServices>().create("notifications", get(named("notifications"))) {
          notifications.sendPending()
        }
      }
    },
  )
}.koin
// end::wire[]
