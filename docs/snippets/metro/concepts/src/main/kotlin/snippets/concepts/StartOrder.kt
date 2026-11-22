package snippets.concepts

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultStartOrder
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.boot.Phase
import com.github.rocketraman.bootable.boot.StartOrder
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

enum class AppPhase : Phase { Secrets }

object AppStartOrder : StartOrder by DefaultStartOrder {
  override val phases = listOf(AppPhase.Secrets) + DefaultStartOrder.phases
}

class SecretsLoader : AppService {
  override fun ordering() = Ordering(AppPhase.Secrets)
}

// tag::wire[]
// boot-config-hoplite-metro's ConfigBinder gives Bootable the tiebreaker, from boot.service.order
@ContributesTo(AppScope::class)
interface StartOrderBindings {
  @Provides fun startOrder(): StartOrder = AppStartOrder  // passed to Bootable

  @Provides @IntoSet @SingleIn(AppScope::class)
  fun secretsLoader(): AppService = SecretsLoader()
}
// end::wire[]
