package snippets.concepts

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultStartOrder
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.boot.Phase
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

enum class AppPhase : Phase { Secrets }

object AppStartOrder : StartOrder by DefaultStartOrder {
  override val phases = listOf(AppPhase.Secrets) + DefaultStartOrder.phases
}

class SecretsLoader : AppService {
  override fun ordering() = Ordering(AppPhase.Secrets)
}

fun orderedKoin() = koinApplication {
  modules(
    // tag::wire[]
    bootableModule(),
    hopliteConfigModule(),                    // the tiebreaker, from boot.service.order
    module {
      single<StartOrder> { AppStartOrder }  // passed to Bootable
      single<AppService>(named<SecretsLoader>()) { SecretsLoader() }
    },
    // end::wire[]
  )
}.koin
