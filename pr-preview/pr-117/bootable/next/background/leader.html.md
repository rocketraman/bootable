# Leader election

> LeaderElection: whether this instance leads, by a Kubernetes Lease, or always, and a fencing token for its writes.

`boot-leader-common` defines `LeaderElection`: whether this instance is the leader of the application’s instances, such as the replicas of a Kubernetes deployment. At most one of them leads at any time, as long as the leader doesn’t pause for too long; its [fencing token](https://rocketraman.github.io/bootable/bootable/next/background/leader.html.md#fencing) covers a leader that does, where the work writes. Use it for work that only one instance should do, such as a [periodic loop](https://rocketraman.github.io/bootable/bootable/next/background/periodic.html.md#gates) that sends pending notifications.

`boot-leader-k8s` elects the leader among an application’s pods with a Kubernetes Lease. It is in the `Infrastructure` phase, so it leads, if it can, before the loops that ask it start, and stops after them.

Work that db-scheduler runs needs no leader: it runs each execution on one instance by locking its row; see [Scheduled tasks (db-scheduler)](https://rocketraman.github.io/bootable/bootable/next/background/scheduler.html.md). For when leadership and fencing are the right tool, and what to use when they aren’t, see [When to lead, and when not to](https://rocketraman.github.io/bootable/bootable/next/background/leader.html.md#right-tool).

## LeaderElection

`leadership` is a `StateFlow<Boolean>`, which is true while this instance leads. `isLeader()` asks whether it leads now, and `awaitLeadership()` suspends until it does:

```kotlin
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
```

`fencingToken` is the current lease term’s fencing token, or null while this instance doesn’t lead; see [Fencing tokens](https://rocketraman.github.io/bootable/bootable/next/background/leader.html.md#fencing).

The [leader gate](https://rocketraman.github.io/bootable/bootable/next/background/periodic.html.md#gates), `PeriodicGate.leader(leaderElection)`, asks it before each run, and cancels a run in progress when `leadership` turns false.

There are two implementations:

- `AlwaysLeader`, in `boot-leader-common`, always leads: for an application that runs as one instance, and for running locally. Its `fencingToken` is always 0.
- `KubernetesLeaderElection`, in `boot-leader-k8s`, holds a Lease.

## Kubernetes

`KubernetesLeaderElection` elects a leader as Kubernetes’s own controllers elect theirs, following client-go’s leader election: the leader is the pod that holds a `coordination.k8s.io/v1` Lease.

- The leader renews the Lease every `retryPeriod`, and gives up leadership when it hasn’t renewed it within `renewDeadline`.
- The other pods try to take the Lease every `retryPeriod`, and take it once it hasn’t been renewed for `leaseDuration`.
- On shutdown, the leader releases the Lease, so another pod takes over at once, rather than after `leaseDuration`.

Starting it makes the first attempt to take the Lease, and fails the start if the API server refuses it access, with a 401 or 403 status, such as when the pod’s service account lacks the permission, since retrying wouldn’t fix that. It then keeps trying, or renewing, in the background, and retries whatever fails.

### Configure

It reads `leader.kubernetes`, where only the Lease’s name is required:

```hocon
leader.kubernetes {
  leaseName = notifications-leader  (1)
  leaseDuration = 30s               (2)
  renewDeadline = 20s               (3)
}
```

- (1) Names the Lease the instances compete for: one for each application, which every instance of it names alike, and no other application does.
- (2) Tolerates longer pauses of the leader, such as for garbage collection, at the cost of a slower takeover when a leader dies without releasing the Lease; by default, 15 seconds.
- (3) Keeps the margin between the two, 10 seconds here, 5 by default, which is how long the leader’s work has to stop, and the longest pause the guarantee survives; see [Timing](https://rocketraman.github.io/bootable/bootable/next/background/leader.html.md#timing). `leaseDuration` must be longer than `renewDeadline`, and `renewDeadline` longer than `retryPeriod`, 2 seconds by default, or decoding the config fails.

To run outside Kubernetes, such as locally, with the same wiring, set `assumeLeader = true` in that environment’s config: the instance skips the election, and always leads, with a fencing token of 0.

The API server’s address comes from `KUBERNETES_SERVICE_HOST` and `KUBERNETES_SERVICE_PORT`, which Kubernetes gives every pod, unless `apiServer` is set. The service account’s token, and its namespace, are read from `serviceAccountDir`, `/var/run/secrets/kubernetes.io/serviceaccount` by default. The others include `namespace` and `identity`, by default the pod’s own namespace and name, `retryPeriod`, `requestTimeout`, `releaseOnShutdown`, and `httpClient` (see [The API server’s client](https://rocketraman.github.io/bootable/bootable/next/background/leader.html.md#the-api-server)): see [all the settings](https://rocketraman.github.io/bootable/bootable/next/api/boot-leader-k8s/com.github.rocketraman.bootable.leader.k8s/-kubernetes-leader-election-config/index.html).

### Permissions

The pod’s service account needs a Role that allows `get`, `create`, and `update` on `leases` in the `coordination.k8s.io` API group, in the Lease’s namespace, bound to it:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: leader-election
rules:
  - apiGroups: [coordination.k8s.io]
    resources: [leases]
    verbs: [get, create, update]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: leader-election
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: leader-election
subjects:
  - kind: ServiceAccount
    name: notifications  # the pod's service account
```

Each request authenticates with the service account’s token, read from its file for each request, since Kubernetes rotates it.

### The API server’s client

It talks to the API server’s REST API through one of the application’s own [Ktor clients](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md), from `boot-client-http-ktor`, rather than through a Kubernetes client library: leader election needs only three calls on one Lease. So the API server’s TLS, timeouts, and retries are configured as every other HTTP call the application makes is, and the engine is the application’s choice.

It uses the client named by `httpClient`, `kubernetes` by default, or the default client if there is none of that name. In a cluster, that client trusts the API server with no TLS config of the application’s: `kubernetesClientDefaults` defaults its trust store to the service account’s CA certificate, `ca.crt`, so `leaseName` is the only setting the election needs. The application’s own config of the client, in `http.client.named.kubernetes`, applies over that default, and its `tls`, if it sets one, replaces it, such as to trust another CA:

```hocon
http.client.named.kubernetes {
  tls.trustStore { path = "/etc/k8s/ca.crt", type = PEM }  (1)
}
```

- (1) Trusts another CA’s certificates in place of the service account’s `ca.crt`, such as for an API server outside the cluster.

The Kodein, Koin, Metro, and Inara flavours contribute the default; with vanilla wiring, pass `kubernetesClientDefaults(config)` to `KtorClientService`, as [Wire](https://rocketraman.github.io/bootable/bootable/next/background/leader.html.md#wire) shows.

The election is in the same phase as the clients, after their `HttpClientMilestones.ClientsReady` milestone, so it stops, releasing the Lease, before they close.

### Timing

Two pods never lead at once, because a leader steps down before another pod can take over:

- The leader steps down `renewDeadline` after it sent its last successful renewal, even if a request is still in flight then.
- Another pod takes the Lease only `leaseDuration` after it saw that renewal, which was after it was sent.
- Both are timed by each pod’s monotonic clock, so the pods' clocks need not agree, and a clock that jumps elects no second leader; the Lease’s own times are only informational.

The leader’s work must stop within the margin between the two, 5 seconds by default, once `leadership` turns false: the leader gate cancels a run in progress then. The guarantee holds as long as the leader doesn’t pause, such as for a long garbage collection or a suspended VM, for longer than that margin, since a paused pod can’t step down: it would then carry on as though it still led. A [fencing token](https://rocketraman.github.io/bootable/bootable/next/background/leader.html.md#fencing) covers that pause, where the work writes.

A leader that stops without releasing the Lease, such as one that crashes, is replaced within about `leaseDuration`. `renewDeadline` must be longer than `retryPeriod`, and `leaseDuration` longer than `renewDeadline`.

## Fencing tokens

A leader that pauses, such as for a long garbage collection or a suspended VM, can’t step down while paused, so when it resumes, it can carry on as though it still led, while another instance leads. A fencing token keeps such a leader’s late writes from overwriting the newer leader’s. Martin Kleppmann’s [How to do distributed locking](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html) explains the problem and the fix.

A _lease term_ is one unbroken stretch of leadership by one instance, from when it starts leading until it stops. Each lease term has one fencing token: `fencingToken` is the current lease term’s, or null while this instance doesn’t lead. It is the same throughout a lease term, and greater in each later lease term, whichever instance leads it. `AlwaysLeader` holds a single permanent lease, with an unlimited lease term, so its token is always 0.

The work reads the token as it starts, skips its writes if it is null, and sends it with each write:

```kotlin
  PeriodicAppService("digest", binder.config("digest.loop"), PeriodicGate.leader(leaderElection)) {
    val token = leaderElection.fencingToken ?: return@PeriodicAppService  // null: this instance doesn't lead
    val position = processNewEntries()
    cursors.advance("digest", position, token)  // false: a later lease term has written it, and this one is over
  }
```

Bootable can’t fence the writes itself: the store must be configured to refuse a write whose token is lower than the highest it has seen. Then, once a later leader has written, a paused leader’s late writes fail, rather than overwrite the newer ones. A good way to do that is to persist the token alongside the data it guards, and make each write conditional on it, with a `WHERE` clause that the writer’s token is at least the persisted one:

```kotlin
// one row for each loop, which the schema's migration inserts
object CursorTable : Table("cursor") {
  val name = varchar("name", 50)
  val position = long("position")       // the last entry the loop has processed
  val fence = long("fence").default(0)  // the highest fencing token that has written the row
  override val primaryKey = PrimaryKey(name)
}

class Cursors(private val db: Database) {
  /** Moves cursor [name] to [position], unless a later lease term has written it; returns whether it did. */
  suspend fun advance(name: String, position: Long, token: Long): Boolean = db.withTransaction {
    val updated = CursorTable.update({ (CursorTable.name eq name) and (CursorTable.fence lessEq token) }) {
      it[CursorTable.position] = position
      it[CursorTable.fence] = token
    }
    updated == 1
  }
}
```

That is, in SQL, `UPDATE cursor SET position = :position, fence = :token WHERE name = :name AND fence ⇐ :token`; when it updates no row, a later leader has written it, and this lease term is over. Each write persists its own token as it succeeds, so the fence only rises. Persist the token in each row the work updates, as here, or in a single row that holds the highest token, updated with the same `WHERE` clause in the same transaction as the work’s writes, rolling the transaction back if it updates no row.

### Kubernetes’s lease terms

`KubernetesLeaderElection`'s lease term is one pod’s unbroken hold of the Lease, and its token is the Lease’s `spec.leaseTransitions`, as of the write that started the lease term. A new lease term increments it: when another pod takes the Lease, and when a pod that stepped down takes it again, even if the Lease still names it, since its earlier lease term’s work might still be running. Renewals leave it as it is.

The tokens increase because the Kubernetes API server, backed by etcd, orders the writes that start lease terms, rather than the pods do. Every write to the Lease is conditional on the `resourceVersion` the pod read, a compare-and-set that etcd applies atomically: a write based on an outdated read fails with a 409 Conflict. So a pod can start a lease term only by writing `leaseTransitions + 1` over the latest Lease, and of two pods that race to take it, one write succeeds and the other must read the Lease again. Each lease term’s token is therefore one more than the previous one’s, whatever the pods' clocks say, or however long one of them pauses.

Deleting the Lease starts the count again, at 0, so a store that has seen a higher token would refuse every leader’s writes: leave the Lease in place, or reset the fences with it.

### When to lead, and when not to

Leadership is hard; use the right tool for the work at hand:

- **State in a store you own**, such as a counter, an aggregate, or a cursor of what has been processed: lead, and fence the writes with the token, as above.
- **Effects outside that store**, such as sending an email or calling a webhook: a fencing token can’t guard these, since the other system doesn’t check it. A leader that pauses mid-send leaves its successor unsure whether the send happened, so it must choose between at-least-once (retry, and maybe send twice) and at-most-once (don’t, and maybe never send). Choose at-least-once, and pass an idempotency key, such as the row’s id, for the receiver to drop duplicates: the effect then happens exactly once.
- **The intent to send, with the change that causes it**: a transactional outbox writes both in one transaction; its relay still delivers at least once, so it needs an idempotent receiver too.
- **Work spread over rows**, such as draining an outbox, or tasks: lock each row, with `SELECT …​ FOR UPDATE SKIP LOCKED`, or use [db-scheduler](https://rocketraman.github.io/bootable/bootable/next/background/scheduler.html.md), rather than elect a leader.

Once the receiver is idempotent, a single leader is only an optimization, to avoid duplicate work.

## Wire

**Vanilla:**

Build the HTTP clients with the election’s defaults, give the election its client, gate the loops on it, and pass both to `Bootable`, since the election is a service too:

```kotlin
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
```

**Kodein:**

Import `kubernetesLeaderElectionModule`, which binds the `LeaderElection` and adds the election to the app-service set, after `ktorClientModule`, with an engine:

```kotlin
fun leaderDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(ktorClientModule)                   // the HTTP clients, from http.client
  bindSingleton<KtorClientEngine> { CioClientEngine() }
  import(kubernetesLeaderElectionModule)     // the LeaderElection, from leader.kubernetes, after ktorClientModule
  import(periodicModule)                     // its loops gated on that LeaderElection
  // ... and the loops
}
```

**Koin:**

Include `kubernetesLeaderElectionModule()`, which defines the `LeaderElection` and adds the election to the app services, with `ktorClientModule()` and an engine:

```kotlin
fun leaderKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    ktorClientModule(),                // the HTTP clients, from http.client
    module { single<KtorClientEngine> { CioClientEngine() } },
    kubernetesLeaderElectionModule(),  // the LeaderElection, from leader.kubernetes, over ktorClientModule()'s clients
    periodicModule(),                  // its loops gated on that LeaderElection
    // ... and the loops
  )
}.koin
```

**Metro:**

`boot-leader-k8s-metro` contributes the `LeaderElection`, and adds the election to the app-service set; `boot-client-http-ktor-metro` contributes the clients, and needs an engine:

```kotlin
// boot-leader-k8s-metro contributes the LeaderElection, and boot-client-http-ktor-metro the HTTP clients it calls
// the API server with; the application chooses their engine.
@ContributesTo(BackgroundScope::class)
interface HttpClientEngineBindings {
  @Provides fun ktorClientEngine(): KtorClientEngine = CioClientEngine()
}
```

**Inara:**

Compose `kubernetesLeaderElectionModule()`, which provides the `LeaderElection` and marks the election `@AppServices`; it requires a `KtorClientModule`, here `ktorClientModule(…​)` with its engine:

```kotlin
@DIModule(composed = true)
interface LeaderAppModule :
  ConfigModule, BootableModule, KtorClientModule, KubernetesLeaderElectionModule, OpenTelemetryApiModule,
  PeriodicModule {
  // ... and the loops
}

fun leaderAppModule() = LeaderAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  ktorClientModule = ktorClientModule(CioClientEngine()),  // the HTTP clients, from http.client, on that engine
  // the LeaderElectionModule, from leader.kubernetes; it requires the KtorClientModule, to call the API server
  kubernetesLeaderElectionModule = kubernetesLeaderElectionModule(),
  openTelemetryApiModule = NoopOpenTelemetryModule,        // required by periodicModule: no tracing of the runs
  periodicModule = periodicModule(),                       // its loops gated on that leaderElection
)
```

`periodicModule` and `boot-periodic-metro` gate the loops on the `LeaderElection` on their own. With no `LeaderElection`, as without `boot-leader-k8s`, every instance runs them.

## Reference

- [`LeaderElection`](https://rocketraman.github.io/bootable/bootable/next/api/boot-leader-common/com.github.rocketraman.bootable.leader/-leader-election/index.html): whether this instance leads, and its fencing token
- [`awaitLeadership`](https://rocketraman.github.io/bootable/bootable/next/api/boot-leader-common/com.github.rocketraman.bootable.leader/await-leadership.html): suspends until it does
- [`AlwaysLeader`](https://rocketraman.github.io/bootable/bootable/next/api/boot-leader-common/com.github.rocketraman.bootable.leader/-always-leader/index.html): always leads
- [`KubernetesLeaderElection`](https://rocketraman.github.io/bootable/bootable/next/api/boot-leader-k8s/com.github.rocketraman.bootable.leader.k8s/-kubernetes-leader-election/index.html): holds a Lease
- [`KubernetesLeaderElectionConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-leader-k8s/com.github.rocketraman.bootable.leader.k8s/-kubernetes-leader-election-config/index.html): its settings
- [`kubernetesClientDefaults`](https://rocketraman.github.io/bootable/bootable/next/api/boot-leader-k8s/com.github.rocketraman.bootable.leader.k8s/kubernetes-client-defaults.html): its HTTP client’s defaults
