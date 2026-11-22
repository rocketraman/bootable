package snippets.http

import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.HealthCheck
import com.github.rocketraman.bootable.boot.HealthChecks
import com.github.rocketraman.bootable.boot.HealthStatus
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.db.jdbc.HikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.JdbcHealthCheckModule
import com.github.rocketraman.bootable.db.jdbc.SqliteConfigSource
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.jdbcHealthCheckModule
import com.github.rocketraman.bootable.server.http.ktor.KtorHealthModule
import com.github.rocketraman.bootable.server.http.ktor.KtorServerEngineModule
import com.github.rocketraman.bootable.server.http.ktor.ktorHealthModule
import com.github.rocketraman.bootable.server.http.ktor.ktorServerCioEngineModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import java.util.concurrent.atomic.AtomicBoolean

/** Whether the application's queue consumer is connected; set by the consumer. */
val consumerConnected = AtomicBoolean(false)

// tag::wire[]
@DIModule
interface HealthChecksModule : @DIModule.Required BootableModule {
  @HealthChecks val consumerCheck: HealthCheck  // one of the HealthLifecycleController's checks
}

fun healthChecksModule() = HealthChecksModulePart(
  consumerCheck = Binding.single {
    HealthCheck("consumer") {
      if (consumerConnected.get()) HealthStatus.Healthy else HealthStatus.Unhealthy("not connected to the queue")
    }
  },
)

// with ServerModule, which serves the probes, and the database's modules, which provide the pool
@DIModule(composed = true)
interface CheckedAppModule :
  ConfigModule, BootableModule, KtorServerEngineModule, KtorHealthModule, ServerModule,
  JdbcDataSourceModule, HikariPoolModule, JdbcHealthCheckModule, HealthChecksModule

fun checkedAppModule() = CheckedAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  ktorServerEngineModule = ktorServerCioEngineModule(),
  ktorHealthModule = ktorHealthModule(),
  serverModule = serverModule(),
  jdbcDataSourceModule = jdbcDataSourceModule(SqliteConfigSource),
  hikariPoolModule = hikariPoolModule(),
  jdbcHealthCheckModule = jdbcHealthCheckModule(),  // readiness down while the database doesn't answer
  healthChecksModule = healthChecksModule(),
)
// end::wire[]
