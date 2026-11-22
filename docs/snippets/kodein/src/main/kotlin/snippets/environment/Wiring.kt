package snippets.environment

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.env.environmentModule
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance

// tag::wire[]
fun appDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(environmentModule)  // sets environment from env.environment, before services start
  // ... the application's services
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
// end::wire[]
