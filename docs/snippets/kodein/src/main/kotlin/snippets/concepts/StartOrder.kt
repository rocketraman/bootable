package snippets.concepts

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultStartOrder
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.boot.Phase
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.bindAppService
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.singleton

enum class AppPhase : Phase { Secrets }

object AppStartOrder : StartOrder by DefaultStartOrder {
  override val phases = listOf(AppPhase.Secrets) + DefaultStartOrder.phases
}

class SecretsLoader : AppService {
  override fun ordering() = Ordering(AppPhase.Secrets)
}

fun orderedDI() = DI {
  // tag::wire[]
  import(bootableModule)
  import(hopliteConfigModule)                     // the tiebreaker, from boot.service.order
  bindSingleton<StartOrder> { AppStartOrder }     // passed to Bootable
  bindAppService { singleton { SecretsLoader() } }
  // end::wire[]
}
