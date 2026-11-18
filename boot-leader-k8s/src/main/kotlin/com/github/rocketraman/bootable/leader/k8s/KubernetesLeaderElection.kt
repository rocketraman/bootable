package com.github.rocketraman.bootable.leader.k8s

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.leader.LeaderElection
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.logging.log4j.kotlin.logger
import java.net.InetAddress
import com.github.rocketraman.bootable.client.http.ktor.HttpClientMilestones
import io.ktor.client.HttpClient
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

// by name, rather than logger(), whose name resolution would need native-image metadata for the calling class
private val log = logger("com.github.rocketraman.bootable.leader.k8s.KubernetesLeaderElection")

/**
 * A [LeaderElection] among the pods of an application in Kubernetes: the leader is the one that holds a
 * `coordination.k8s.io/v1` Lease, as Kubernetes's own controllers elect theirs, following client-go's leader election.
 *
 * It is an [AppService], in the [DefaultPhase.Infrastructure] phase, so it leads, if it can, before the services that
 * ask it, such as `boot-periodic`'s loops, start, and stops after them. Starting it makes the first attempt to take the
 * Lease, and fails the start if the API server refuses it access (a 401 or 403 status), such as when the pod's service
 * account lacks the permission; it then keeps trying, or renewing, every [KubernetesLeaderElectionConfig.retryPeriod]
 * in the background, retrying whatever fails. On shutdown it stops, and the leader releases the Lease, so another pod
 * takes over at once.
 *
 * It talks to the API server's REST API through [httpClient], one of the application's Ktor clients from
 * `boot-client-http-ktor`, such as the one named by [KubernetesLeaderElectionConfig.httpClient], whose TLS settings
 * trust the API server's certificate authority. Each request authenticates with the pod's service account token, read
 * from its file for each request, since Kubernetes rotates it. The service account needs a Role that allows `get`,
 * `create`, and `update` on `leases` in the `coordination.k8s.io` API group, in the Lease's namespace.
 *
 * ```
 * val config = binder.config<KubernetesLeaderElectionConfig>("leader.kubernetes")
 * KubernetesLeaderElection(config, clients.getOrDefault(config.httpClient))
 * ```
 *
 * [clock] dates the Lease's renewals, and times its expiry.
 */
class KubernetesLeaderElection(
  private val config: KubernetesLeaderElectionConfig,
  httpClient: HttpClient,
  private val clock: Clock = Clock.System,
  environment: Map<String, String> = System.getenv(),
) : AppService, LeaderElection {

  private val state = MutableStateFlow(config.assumeLeader)
  override val leadership: StateFlow<Boolean> = state.asStateFlow()

  /** This instance's identity: the Lease's holder while it leads. */
  val identity: String = config.identity ?: environment["HOSTNAME"]?.ifBlank { null } ?: localHostName()
    ?: error("No identity for leader election: set leader.kubernetes.identity, or the HOSTNAME environment variable")

  /** The Lease's holder, as this instance last saw it, or null if it hasn't, or nobody holds it. */
  @Volatile
  var holderIdentity: String? = null
    private set

  private val client: LeaseClient? = if (config.assumeLeader) null else leaseClient(httpClient, environment)
  private var scope: CoroutineScope? = null
  private var election: Job? = null

  // the Lease as this instance last saw it, and when, by its own clock: another holder's lease expires leaseDuration
  // after this instance saw it last change, so the instances' clocks need not agree
  private var observedSpec: LeaseSpec? = null
  private var observedAt: Instant = Instant.DISTANT_PAST
  private var observedLease: Lease? = null

  private fun leaseClient(httpClient: HttpClient, environment: Map<String, String>): LeaseClient {
    val serviceAccount = Path(config.serviceAccountDir)
    val namespace = config.namespace
      ?: serviceAccount.resolve("namespace").takeIf { it.exists() }?.readText()?.trim()
      ?: error("No namespace for leader election: set leader.kubernetes.namespace, or run in a pod with a service account")
    val apiServer = config.apiServer ?: run {
      val host = environment["KUBERNETES_SERVICE_HOST"]
      val port = environment["KUBERNETES_SERVICE_PORT"]
      require(host != null && port != null) {
        "No API server for leader election: set leader.kubernetes.apiServer, or run in a pod"
      }
      // an IPv6 address needs brackets in a URL
      "https://${if (':' in host) "[$host]" else host}:$port"
    }
    return LeaseClient(
      apiServer, namespace, config.leaseName, tokenFile(serviceAccount.resolve("token")), httpClient,
      config.requestTimeout,
    )
  }

  // after the HTTP clients, in the same phase, so it stops, releasing the Lease, before they close
  override fun ordering() = Ordering(DefaultPhase.Infrastructure, after = setOf(HttpClientMilestones.ClientsReady))

  override fun start() {
    if (client == null) {
      log.info { "Leader election is off (leader.kubernetes.assumeLeader): $identity always leads" }
      return
    }
    // the first attempt fails the start if the API server refuses access, which retrying wouldn't fix
    runBlocking { attempt(firstAttempt = true) }
    val scope = CoroutineScope(
      SupervisorJob() + Dispatchers.IO + CoroutineName("leader-election") + CoroutineExceptionHandler { _, e ->
        log.error(e) { "Leader election stopped unexpectedly; $identity no longer leads" }
        state.value = false
      }
    )
    this.scope = scope
    election = scope.launch { elect() }
  }

  override fun shutdown() {
    val client = client ?: return
    runBlocking {
      election?.cancelAndJoin()
      scope?.cancel()
      if (state.value) {
        state.value = false
        if (config.releaseOnShutdown) release(client)
      }
    }
  }

  /** Keeps trying to lead, and, while leading, renewing the Lease, until cancelled. */
  private suspend fun elect() {
    var lastRenewed = TimeSource.Monotonic.markNow()
    while (true) {
      delay(config.retryPeriod)
      val wasLeader = state.value
      when (attempt()) {
        Attempt.Held -> lastRenewed = TimeSource.Monotonic.markNow()
        Attempt.HeldByOther -> if (wasLeader) lose("another instance, $holderIdentity, holds the lease")
        Attempt.Failed -> if (wasLeader && lastRenewed.elapsedNow() >= config.renewDeadline) {
          lose("it couldn't renew the lease within ${config.renewDeadline}")
        }
      }
    }
  }

  private fun lose(reason: String) {
    state.value = false
    log.warn { "$identity no longer leads: $reason" }
  }

  private enum class Attempt { Held, HeldByOther, Failed }

  /** Tries to take, or renew, the Lease, once; and when it does, leads. */
  @Suppress("TooGenericExceptionCaught")
  private suspend fun attempt(firstAttempt: Boolean = false): Attempt = try {
    if (tryAcquireOrRenew()) {
      if (!state.value) {
        state.value = true
        log.info { "$identity leads, holding lease ${config.leaseName}" }
      }
      Attempt.Held
    } else {
      Attempt.HeldByOther
    }
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    if (firstAttempt && e is KubernetesApiException && e.status in ACCESS_DENIED) throw e
    // a conflict is another instance updating the lease first, which is the election working
    if (e is KubernetesApiException && e.status == HTTP_CONFLICT) {
      log.debug { "$identity lost a race for lease ${config.leaseName}: ${e.message}" }
    } else {
      log.warn(e) { "$identity couldn't take or renew lease ${config.leaseName}; retrying in ${config.retryPeriod}" }
    }
    Attempt.Failed
  }

  /** Takes or renews the Lease, and returns whether it holds it; false if another instance does. */
  private suspend fun tryAcquireOrRenew(): Boolean {
    val client = checkNotNull(client)
    val now = clock.now()
    val leaseDurationSeconds = config.leaseDuration.inWholeSeconds.toInt()
    val lease = client.get()
    if (lease == null) {
      val spec = LeaseSpec(identity, leaseDurationSeconds, formatMicroTime(now), formatMicroTime(now), 0)
      observe(client.create(spec), now)
      return true
    }
    if (lease.spec != observedSpec) {
      observedSpec = lease.spec
      observedAt = now
    }
    observedLease = lease
    val holder = lease.spec.holderIdentity
    holderIdentity = holder?.ifEmpty { null }
    val duration = lease.spec.leaseDurationSeconds?.seconds ?: config.leaseDuration
    if (!holder.isNullOrEmpty() && holder != identity && observedAt + duration > now) return false

    val spec = if (holder == identity) {
      lease.spec.copy(leaseDurationSeconds = leaseDurationSeconds, renewTime = formatMicroTime(now))
    } else {
      LeaseSpec(
        identity, leaseDurationSeconds, formatMicroTime(now), formatMicroTime(now), (lease.spec.leaseTransitions ?: 0) + 1,
      )
    }
    observe(client.update(lease.copy(spec = spec)), now)
    return true
  }

  private fun observe(lease: Lease, now: Instant) {
    observedLease = lease
    observedSpec = lease.spec
    observedAt = now
    holderIdentity = lease.spec.holderIdentity?.ifEmpty { null }
  }

  /** Releases the Lease, as client-go does: no holder, and a lease of a second, so another instance takes it at once. */
  @Suppress("TooGenericExceptionCaught")
  private suspend fun release(client: LeaseClient) {
    val lease = observedLease ?: return
    if (lease.spec.holderIdentity != identity) return
    val now = formatMicroTime(clock.now())
    try {
      withTimeout(config.requestTimeout) {
        observe(
          client.update(lease.copy(spec = lease.spec.copy(holderIdentity = "", leaseDurationSeconds = 1, renewTime = now))),
          clock.now(),
        )
      }
      log.info { "$identity released lease ${config.leaseName}" }
    } catch (e: Exception) {
      log.warn(e) { "$identity couldn't release lease ${config.leaseName}; another instance takes it once it expires" }
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private fun localHostName(): String? = try {
    // a pod's host name is its name too; HOSTNAME isn't always exported, such as on some CI runners
    InetAddress.getLocalHost().hostName.ifBlank { null }
  } catch (e: Exception) {
    log.debug(e) { "No local host name" }
    null
  }

  override fun toString() = "KubernetesLeaderElection(${config.leaseName}, $identity)"

  companion object {
    private const val HTTP_CONFLICT = 409
    private val ACCESS_DENIED = setOf(401, 403)
  }
}
