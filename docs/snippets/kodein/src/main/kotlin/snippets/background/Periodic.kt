package snippets.background

import com.github.rocketraman.bootable.boot.bindAppService
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.periodic.PeriodicServices
import com.github.rocketraman.bootable.periodic.periodicModule
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import org.kodein.di.new
import org.kodein.di.singleton

// tag::wire[]
fun periodicDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(periodicModule)  // PeriodicServices, gated on the LeaderElection binding, if there is one

  bindSingleton { new(::Notifications) }
  bindAppService {
    singleton {
      instance<PeriodicServices>().create("notifications", instance<ConfigBinder>().config("notifications.loop")) {
        instance<Notifications>().sendPending()
      }
    }
  }
}
// end::wire[]
