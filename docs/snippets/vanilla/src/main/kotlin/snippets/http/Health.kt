package snippets.http

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.withHealthLifecycleController
import com.github.rocketraman.bootable.boot.HealthCheck
import com.github.rocketraman.bootable.boot.HealthStatus
import com.github.rocketraman.bootable.boot.buildInfo
import com.github.rocketraman.bootable.boot.defaultLifecycleControllers
import com.github.rocketraman.bootable.boot.readinessConfig
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.JdbcHealthCheck
import com.github.rocketraman.bootable.server.http.ktor.KtorHealth
import com.github.rocketraman.bootable.server.http.ktor.ktorHealthConfig
import io.ktor.server.cio.*
import java.util.concurrent.atomic.AtomicBoolean

/** Whether the application's queue consumer is connected; set by the consumer. */
val consumerConnected = AtomicBoolean(false)

// tag::check[]
val consumerCheck = HealthCheck("consumer") {
  if (consumerConnected.get()) HealthStatus.Healthy else HealthStatus.Unhealthy("not connected to the queue")
}
// end::check[]

fun healthApp(binder: ConfigBinder, database: JdbcDataSourceService): Bootable {
  // tag::wire[]
  val health = HealthLifecycleController(
    setOf(consumerCheck, JdbcHealthCheck(database.dataSource)),  // the readiness checks
    readinessConfig(binder),                                     // health.readiness: the drain delay
  )
  val ktorHealth = KtorHealth(
    health,
    ktorHealthConfig(binder),  // health.ktor: the paths, and the listener
    buildInfo(binder),         // build: served at /health/info
  )

  return Bootable(
    setOf(
      database,
      GreetingServer(binder.config("server"), ktorHealth),  // installHealth(health) in its module()
      ktorHealth.listener(CIO),                             // the probes' own listener, if hostPort is set
    ),
    defaultLifecycleControllers().withHealthLifecycleController(health),
  )
  // end::wire[]
}
