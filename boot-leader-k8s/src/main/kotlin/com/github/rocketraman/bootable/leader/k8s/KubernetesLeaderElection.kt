package com.github.rocketraman.bootable.leader.k8s

import com.github.rocketraman.bootable.boot.AdvancedAppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.leader.LeaderElection
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
import kotlin.time.TimeSource

// by name, rather than logger(), whose name resolution would need native-image metadata for the calling class
private val log = logger("com.github.rocketraman.bootable.leader.k8s.KubernetesLeaderElection")

/**
 * A [LeaderElection] among the pods of an application in Kubernetes: the leader is the one that holds a
 * `coordination.k8s.io/v1` Lease, as Kubernetes's own controllers elect theirs, following client-go's leader election.
 *
 * It is an [AdvancedAppService], in the [DefaultPhase.Infrastructure] phase, so it leads, if it can, before the
 * services that ask it, such as `boot-periodic`'s loops, start, and stops after them. Starting it makes the first
 * attempt to take the Lease, and fails the start if the API server refuses it access (a 401 or 403 status), such as
 * when the pod's service account lacks the permission; it then keeps trying, or renewing, every
 * [KubernetesLeaderElectionConfig.retryPeriod] in the background, retrying whatever fails. On shutdown it stops, and
 * the leader releases the Lease, so another pod takes over at once. The election runs in the service's scope.
 *
 * Leadership is conservative, so that two instances never lead at once, as long as neither pauses, such as for a
 * garbage collection, longer than the margin between the lease duration and the renew deadline:
 *
 * - A leader stops leading [KubernetesLeaderElectionConfig.renewDeadline] after it last sent a request that renewed
 *   the Lease, even if a request is still in flight then, while another instance takes the Lease only
 *   [KubernetesLeaderElectionConfig.leaseDuration] after it saw the renewal, which was after it was sent.
 * - Both are timed by the monotonic clock, and the Lease's own times, by [clock], are only informational, so neither
 *   the instances' clocks need to agree, nor a clock that jumps elects a second leader.
 *
 * Within that margin, work that only the leader does must stop once [leadership] turns false: `boot-periodic`'s
 * `PeriodicGate.leader` cancels a run in progress then.
 *
 * The [fencingToken] covers a pause longer than that margin, where the work writes: it is the Lease's
 * `spec.leaseTransitions` as of the lease term this instance holds, which each new lease term increments, whether
 * another instance takes the Lease, or this one takes it again after it stopped leading, and which renewals leave as it
 * is. Deleting the Lease starts the count again, at 0, so a store that has seen a higher token would refuse every
 * leader's writes.
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
 * [clock] dates the Lease's acquisitions and renewals, for those reading it; it times nothing.
 */
class KubernetesLeaderElection(
  private val config: KubernetesLeaderElectionConfig,
  httpClient: HttpClient,
  private val clock: Clock = Clock.System,
  environment: Map<String, String> = System.getenv(),
) : AdvancedAppService, LeaderElection {

  private val state = MutableStateFlow(config.assumeLeader)
  override val leadership: StateFlow<Boolean> = state.asStateFlow()

  // the lease term this instance holds, or last held, which is its fencing token: the Lease's leaseTransitions as it
  // took the Lease. Set before it leads, so that a reader that sees it lead reads its current lease term
  @Volatile
  private var leaseTerm: Long = 0

  /**
   * The Lease's `spec.leaseTransitions` as of this instance's current lease term, while it leads, or null; with
   * [KubernetesLeaderElectionConfig.assumeLeader], always 0.
   */
  override val fencingToken: Long? get() = if (state.value) leaseTerm else null

  /** This instance's identity: the Lease's holder while it leads. */
  val identity: String = config.identity ?: environment["HOSTNAME"]?.ifBlank { null } ?: localHostName()
    ?: error("No identity for leader election: set leader.kubernetes.identity, or the HOSTNAME environment variable")

  /** The Lease's holder, as this instance last saw it, or null if it hasn't, or nobody holds it. */
  @Volatile
  var holderIdentity: String? = null
    private set

  private val client: LeaseClient? = if (config.assumeLeader) null else leaseClient(httpClient, environment)
  private var election: Job? = null

  // the Lease as this instance last saw it, and when, by the monotonic clock: another holder's lease expires
  // leaseDuration after this instance saw it last change, so the instances' clocks need not agree, nor can a clock
  // that jumps expire it early
  private var observedSpec: LeaseSpec? = null
  private var observedAt: TimeSource.Monotonic.ValueTimeMark? = null
  private var observedLease: Lease? = null

  // when this instance last sent a request that took or renewed the Lease: it leads until renewDeadline after then
  private var renewedAt: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()

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

  override suspend fun start(scope: CoroutineScope) {
    if (client == null) {
      log.info { "Leader election is off (leader.kubernetes.assumeLeader): $identity always leads" }
      return
    }
    // the first attempt fails the start if the API server refuses access, which retrying wouldn't fix
    withContext(Dispatchers.IO) { attempt(firstAttempt = true) }
    election = scope.launch(Dispatchers.IO + CoroutineName("leader-election")) {
      // a failure stops this instance from leading, rather than the application: the others can lead
      @Suppress("TooGenericExceptionCaught")
      try {
        elect()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        log.error(e) { "Leader election stopped unexpectedly; $identity no longer leads" }
        state.value = false
      }
    }
  }

  override suspend fun shutdown() {
    val client = client ?: return
    election?.cancelAndJoin()
    if (state.value) {
      state.value = false
      if (config.releaseOnShutdown) withContext(Dispatchers.IO) { release(client) }
    }
  }

  /**
   * Keeps trying to lead, and, while leading, renewing the Lease, until cancelled. While leading, it stops at the
   * renew deadline, whether or not a request is in flight then.
   */
  private suspend fun elect() {
    while (true) {
      if (!state.value) {
        delay(config.retryPeriod)
        attempt()
        continue
      }
      val untilDeadline = -(renewedAt + config.renewDeadline).elapsedNow()
      when (withTimeoutOrNull(untilDeadline) { delay(config.retryPeriod); attempt() }) {
        null -> lose("it couldn't renew the lease within ${config.renewDeadline}")
        Attempt.HeldByOther -> lose("another instance, $holderIdentity, holds the lease")
        Attempt.Held, Attempt.Failed -> Unit
      }
    }
  }

  private fun lose(reason: String) {
    state.value = false
    log.warn { "$identity no longer leads: $reason" }
  }

  private enum class Attempt { Held, HeldByOther, Failed }

  /**
   * Tries to take, or renew, the Lease, once; and when it does, leads, unless the renew deadline passed since it sent
   * the request.
   */
  @Suppress("TooGenericExceptionCaught")
  private suspend fun attempt(firstAttempt: Boolean = false): Attempt = try {
    // before the requests: the other instances see the renewal after it is sent
    val sent = TimeSource.Monotonic.markNow()
    val held = tryAcquireOrRenew()
    if (held != null) {
      if (sent.elapsedNow() >= config.renewDeadline) {
        log.warn { "$identity took lease ${config.leaseName}, but too slowly to lead; retrying in ${config.retryPeriod}" }
        return Attempt.Failed
      }
      renewedAt = sent
      if (!state.value) {
        leaseTerm = held
        state.value = true
        log.info { "$identity leads, holding lease ${config.leaseName}, with fencing token $held" }
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

  /**
   * Takes or renews the Lease, and returns the lease term it holds, the Lease's `leaseTransitions`, which is its
   * fencing token; null if another instance holds it. It renews the Lease only while it leads: otherwise, even if the
   * Lease still names it, it takes it for a new lease term, so that its work from an earlier lease term, which might
   * still be running, has a lower fencing token.
   */
  private suspend fun tryAcquireOrRenew(): Long? {
    val client = checkNotNull(client)
    val now = clock.now()
    val leaseDurationSeconds = config.leaseDuration.inWholeSeconds.toInt()
    val lease = client.get()
    if (lease == null) {
      val spec = LeaseSpec(identity, leaseDurationSeconds, formatMicroTime(now), formatMicroTime(now), 0)
      return observe(client.create(spec)).leaseTerm
    }
    val seen = if (lease.spec != observedSpec) {
      TimeSource.Monotonic.markNow().also {
        observedSpec = lease.spec
        observedAt = it
      }
    } else {
      checkNotNull(observedAt)
    }
    observedLease = lease
    val holder = lease.spec.holderIdentity
    holderIdentity = holder?.ifEmpty { null }
    val duration = lease.spec.leaseDurationSeconds?.seconds ?: config.leaseDuration
    if (!holder.isNullOrEmpty() && holder != identity && seen.elapsedNow() < duration) return null

    val spec = if (holder == identity && state.value) {
      lease.spec.copy(leaseDurationSeconds = leaseDurationSeconds, renewTime = formatMicroTime(now))
    } else {
      LeaseSpec(
        identity, leaseDurationSeconds, formatMicroTime(now), formatMicroTime(now), (lease.spec.leaseTransitions ?: 0) + 1,
      )
    }
    return observe(client.update(lease.copy(spec = spec))).leaseTerm
  }

  private fun observe(lease: Lease): Lease {
    observedLease = lease
    observedSpec = lease.spec
    observedAt = TimeSource.Monotonic.markNow()
    holderIdentity = lease.spec.holderIdentity?.ifEmpty { null }
    return lease
  }

  private val Lease.leaseTerm: Long get() = (spec.leaseTransitions ?: 0).toLong()

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
