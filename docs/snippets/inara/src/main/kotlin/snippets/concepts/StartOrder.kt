package snippets.concepts

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.DefaultStartOrder
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.boot.Phase
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

enum class AppPhase : Phase { Secrets }

object AppStartOrder : StartOrder by DefaultStartOrder {
  override val phases = listOf(AppPhase.Secrets) + DefaultStartOrder.phases
}

class SecretsLoader : AppService {
  override fun ordering() = Ordering(AppPhase.Secrets)
}

// tag::wire[]
@DIModule(composed = true)
interface OrderedAppModule : ConfigModule, BootableModule {
  @AppServices val secretsLoader: SecretsLoader
}

fun orderedAppModule() = OrderedAppModule(
  configModule = hopliteConfigModule(),                         // the tiebreaker, from boot.service.order
  bootableModule = bootableModule(startOrder = AppStartOrder),  // a parameter, passed to Bootable
  secretsLoader = Binding.single { SecretsLoader() },
)
// end::wire[]
