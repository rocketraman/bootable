package com.github.rocketraman.bootable.leader.k8s

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The settings of [KubernetesLeaderElection], decoded from the `leader.kubernetes` config section. Only [leaseName] is
 * required:
 *
 * ```
 * leader {
 *   kubernetes {
 *     leaseName = "entries-leader"
 *   }
 * }
 * ```
 *
 * The timings follow Kubernetes's own controllers, and client-go's leader election, which this follows: a leader
 * renews its lease every [retryPeriod], and gives up leadership if it hasn't renewed it for [renewDeadline]; another
 * instance takes over once the lease hasn't been renewed for [leaseDuration]. So a leader that stops, without releasing
 * its lease, is replaced within about [leaseDuration], and there are never two leaders as long as the instances'
 * clocks run at about the same rate.
 *
 * @property leaseName the name of the `coordination.k8s.io/v1` Lease the instances compete for: one for each
 *   application, which every instance of it names alike, and no other application does.
 * @property namespace the Lease's namespace. By default, the pod's own, from its service account.
 * @property identity this instance's identity, the Lease's holder while it leads. By default, the `HOSTNAME`
 *   environment variable, which Kubernetes sets to the pod's name, or else the host's name, which in a pod is the pod's
 *   name too. It must differ between the instances.
 * @property leaseDuration how long the Lease holds without being renewed, after which another instance may take it.
 * @property renewDeadline how long the leader keeps trying to renew the Lease before it gives up leadership. Shorter
 *   than [leaseDuration], so that the leader knows it has lost the Lease before another instance can take it.
 * @property retryPeriod how often the leader renews the Lease, and the others try to take it.
 * @property requestTimeout how long each request to the API server may take.
 * @property releaseOnShutdown whether the leader releases the Lease on shutdown, so that another instance takes over
 *   at once, rather than after [leaseDuration].
 * @property assumeLeader whether to skip the election and always lead: for running outside Kubernetes, such as
 *   locally, with the same wiring.
 * @property apiServer the API server's URL. By default, `https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT`,
 *   from the environment Kubernetes gives every pod.
 * @property serviceAccountDir where the pod's service account is mounted: its token, which authenticates each request,
 *   and its namespace, the default [namespace]. Without a token file, requests carry no token.
 * @property httpClient the name of the `boot-client-http-ktor` client to call the API server with. In a cluster, its
 *   trust store defaults to the service account's `ca.crt` (see [kubernetesClientDefaults]), so it trusts the API
 *   server with no config of its own.
 */
data class KubernetesLeaderElectionConfig(
  val leaseName: String,
  val namespace: String? = null,
  val identity: String? = null,
  val leaseDuration: Duration = 15.seconds,
  val renewDeadline: Duration = 10.seconds,
  val retryPeriod: Duration = 2.seconds,
  val requestTimeout: Duration = 5.seconds,
  val releaseOnShutdown: Boolean = true,
  val assumeLeader: Boolean = false,
  val apiServer: String? = null,
  val serviceAccountDir: String = "/var/run/secrets/kubernetes.io/serviceaccount",
  val httpClient: String = "kubernetes",
) {
  init {
    require(leaseName.isNotBlank()) { "The lease name must not be blank" }
    require(retryPeriod.isPositive()) { "The retry period must be positive: $retryPeriod" }
    require(requestTimeout.isPositive()) { "The request timeout must be positive: $requestTimeout" }
    require(renewDeadline > retryPeriod) {
      "The renew deadline ($renewDeadline) must be longer than the retry period ($retryPeriod)"
    }
    require(leaseDuration > renewDeadline) {
      "The lease duration ($leaseDuration) must be longer than the renew deadline ($renewDeadline)"
    }
    require(leaseDuration.inWholeSeconds >= 1) { "The lease duration must be at least a second: $leaseDuration" }
  }
}
