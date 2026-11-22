package snippets.concepts

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.DefaultStartOrder
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.boot.Phase
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.boot.milestone
import com.github.rocketraman.bootable.boot.serviceOrderConfig
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.db.jdbc.DbMilestones
import javax.sql.DataSource

// tag::phase[]
class OrderConsumer : AppService {
  // brings work in, so it starts once everything it uses has, and stops first
  override fun ordering() = Ordering(DefaultPhase.Serving)
}
// end::phase[]

// tag::milestones[]
object PricingMilestones {
  val RatesLoaded = milestone("pricing.rates.loaded")
}

class ExchangeRates : AppService {
  override suspend fun start() { /* load today's rates */ }
  override fun ordering() = Ordering(provides = setOf(PricingMilestones.RatesLoaded))
}

class PriceCalculator(private val rates: ExchangeRates) : AppService {
  override suspend fun start() { /* compute the prices from the rates */ }
  override fun ordering() = Ordering(after = setOf(PricingMilestones.RatesLoaded))
}
// end::milestones[]

// tag::requires[]
class ReportService(private val dataSource: DataSource) : AppService {
  // expects the schema to be migrated, by whichever migration tool the application uses
  override fun ordering() = Ordering(requires = setOf(DbMilestones.SchemaMigrated))
}
// end::requires[]

// tag::custom[]
enum class AppPhase : Phase { Secrets }

object AppStartOrder : StartOrder by DefaultStartOrder {
  override val phases = listOf(AppPhase.Secrets) + DefaultStartOrder.phases
}

class SecretsLoader : AppService {
  override fun ordering() = Ordering(AppPhase.Secrets)
}
// end::custom[]

fun orderedApplication() = boot {
  val binder = hopliteConfigBinder()
  val rates = ExchangeRates()
  // tag::wire[]
  Bootable(
    setOf(SecretsLoader(), rates, PriceCalculator(rates), OrderConsumer()),
    startOrder = AppStartOrder,
    tiebreaker = serviceOrderConfig(binder).tiebreaker,  // from boot.service.order
  )
  // end::wire[]
}
