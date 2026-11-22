package snippets.background

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.leader.LeaderElection
import com.github.rocketraman.bootable.leader.awaitLeadership
import com.github.rocketraman.bootable.leader.k8s.KubernetesLeaderElection
import com.github.rocketraman.bootable.leader.k8s.KubernetesLeaderElectionConfig
import com.github.rocketraman.bootable.leader.k8s.kubernetesClientDefaults
import com.github.rocketraman.bootable.periodic.PeriodicGate
import com.github.rocketraman.bootable.periodic.PeriodicServices
import org.apache.logging.log4j.kotlin.logger

// tag::use[]
class Housekeeping(private val leaderElection: LeaderElection) {
  private val log = logger()

  /** Asks whether this instance leads now. */
  fun cleanUpIfLeader() {
    if (leaderElection.isLeader()) cleanUp()
  }

  /** Suspends until this instance leads. */
  suspend fun cleanUpOnceLeading() {
    leaderElection.awaitLeadership()
    cleanUp()
  }

  /** Follows each change of leadership, as it happens. */
  suspend fun logLeadership() {
    leaderElection.leadership.collect { leading -> log.info { if (leading) "Leading" else "Not leading" } }
  }

  private fun cleanUp() {
    // ...
  }
}
// end::use[]

fun leaderApp(binder: ConfigBinder): Bootable {
  val notifications = Notifications()
  // tag::wire[]
  val leaderConfig = binder.config<KubernetesLeaderElectionConfig>("leader.kubernetes")
  val clients = KtorClientService(
    binder.config("http.client"),
    CioClientEngine(),
    defaults = listOf(kubernetesClientDefaults(leaderConfig)),  // in a pod, the client trusts the API server
  )
  val leaderElection = KubernetesLeaderElection(leaderConfig, clients.getOrDefault(leaderConfig.httpClient))
  val periodic = PeriodicServices(PeriodicGate.leader(leaderElection))  // the loops run on the leader only

  return Bootable(
    setOf(
      clients,
      leaderElection,  // it is a service too: it takes part in the election while the application runs
      periodic.create("notifications", binder.config("notifications.loop")) { notifications.sendPending() },
    ),
  )
  // end::wire[]
}
