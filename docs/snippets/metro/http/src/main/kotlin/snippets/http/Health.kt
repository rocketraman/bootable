package snippets.http

import com.github.rocketraman.bootable.boot.HealthCheck
import com.github.rocketraman.bootable.boot.HealthStatus
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import java.util.concurrent.atomic.AtomicBoolean

/** Whether the application's queue consumer is connected; set by the consumer. */
val consumerConnected = AtomicBoolean(false)

// tag::wire[]
// BootableDefaultBindings, from boot-metro, provides Health with the checks of the Set<HealthCheck>
@ContributesTo(HttpScope::class)
interface HealthBindings {
  @Provides @IntoSet
  fun consumerCheck(): HealthCheck = HealthCheck("consumer") {
    if (consumerConnected.get()) HealthStatus.Healthy else HealthStatus.Unhealthy("not connected to the queue")
  }
}
// end::wire[]
