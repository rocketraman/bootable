package snippets.http

import com.github.rocketraman.bootable.boot.HealthCheck
import com.github.rocketraman.bootable.boot.HealthStatus
import com.github.rocketraman.bootable.db.jdbc.koin.jdbcHealthCheckModule
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.atomic.AtomicBoolean

/** Whether the application's queue consumer is connected; set by the consumer. */
val consumerConnected = AtomicBoolean(false)

// tag::wire[]
// with serverModule, which serves the probes, and the database's modules, which provide the pool
val healthChecksModule = module {
  single<HealthCheck>(named("consumer")) {
    HealthCheck("consumer") {
      if (consumerConnected.get()) HealthStatus.Healthy else HealthStatus.Unhealthy("not connected to the queue")
    }
  }
  includes(jdbcHealthCheckModule())  // readiness down while the database doesn't answer
}
// end::wire[]
