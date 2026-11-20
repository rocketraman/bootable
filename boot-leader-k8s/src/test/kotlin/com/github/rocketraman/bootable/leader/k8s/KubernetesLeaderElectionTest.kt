package com.github.rocketraman.bootable.leader.k8s

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.startPlan
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

private const val NAMESPACE = "apps"
private const val LEASE = "entries-leader"

/**
 * The HTTP client the elections call the fake API server with, as an application's Ktor client would. It is warmed
 * up first, since a cold JVM's first request can take longer than the tests' short timings.
 */
private val http = HttpClient(Java).also { client ->
  FakeApiServer(emptySet()).use { server -> runBlocking { client.get(server.url + "/apis/coordination.k8s.io/v1/namespaces/") } }
}

private fun eventually(timeout: Duration = 5.seconds, condition: () -> Boolean) {
  val start = TimeSource.Monotonic.markNow()
  while (!condition()) {
    check(start.elapsedNow() < timeout) { "Condition not met within $timeout" }
    Thread.sleep(10)
  }
}

private fun sleep(duration: Duration) = Thread.sleep(duration.inWholeMilliseconds)

/** A temporary directory laid out as a pod's mounted service account: its token and namespace. */
@OptIn(ExperimentalPathApi::class)
private suspend fun withServiceAccounts(vararg tokens: String, block: suspend (Map<String, Path>) -> Unit) {
  val dirs = tokens.associateWith { token ->
    createTempDirectory("service-account").also {
      it.resolve("token").writeText("$token\n")
      it.resolve("namespace").writeText(NAMESPACE)
    }
  }
  try {
    block(dirs)
  } finally {
    dirs.values.forEach { it.deleteRecursively() }
  }
}

/** Fast timings, so the tests take a second or two: the lease duration can't be shorter than a second. */
private fun testConfig(server: FakeApiServer, serviceAccount: Path, identity: String) = KubernetesLeaderElectionConfig(
  leaseName = LEASE,
  identity = identity,
  leaseDuration = 1.seconds,
  renewDeadline = 600.milliseconds,
  retryPeriod = 100.milliseconds,
  requestTimeout = 1.seconds,
  apiServer = server.url,
  serviceAccountDir = serviceAccount.toString(),
)

private fun FakeApiServer.holder(): String? =
  lease(NAMESPACE, LEASE)?.get("spec")?.jsonObject?.get("holderIdentity")?.jsonPrimitive?.content

private fun FakeApiServer.transitions(): Int? =
  lease(NAMESPACE, LEASE)?.get("spec")?.jsonObject?.get("leaseTransitions")?.jsonPrimitive?.int

private fun FakeApiServer.spec(field: String): String? =
  lease(NAMESPACE, LEASE)?.get("spec")?.jsonObject?.get(field)?.jsonPrimitive?.content

/** The scope the elections run in, as a service's scope would be: each one's shutdown cancels its own election. */
private val electionScope = CoroutineScope(SupervisorJob())

/** Starts the election, as Bootable does. */
private suspend fun KubernetesLeaderElection.start() = start(electionScope)

private suspend inline fun <T> List<KubernetesLeaderElection>.use(block: suspend (List<KubernetesLeaderElection>) -> T): T = try {
  block(this)
} finally {
  forEach { it.shutdown() }
}

val KubernetesLeaderElectionTest by testSuite {
  test("a lone instance creates the lease, and leads") {
    FakeApiServer(setOf("token-a")).use { server ->
      withServiceAccounts("token-a") { accounts ->
        listOf(KubernetesLeaderElection(testConfig(server, accounts.getValue("token-a"), "pod-a"), http)).use { (a) ->
          a.start()
          // the first attempt is part of the start, so it leads as soon as it has started
          assert(a.isLeader())
          assert(server.holder() == "pod-a")
          assert(server.transitions() == 0)
          assert(server.spec("leaseDurationSeconds") == "1")
          assert(a.holderIdentity == "pod-a")
        }
      }
    }
  }

  test("authenticates with the service account's token, in its namespace") {
    FakeApiServer(setOf("token-a")).use { server ->
      withServiceAccounts("token-a") { accounts ->
        listOf(KubernetesLeaderElection(testConfig(server, accounts.getValue("token-a"), "pod-a"), http)).use { (a) ->
          a.start()
          assert(server.requests.isNotEmpty())
          assert(server.requests.all { (_, path, token) -> token == "token-a" && "/namespaces/$NAMESPACE/leases" in path })
        }
      }
    }
  }

  test("keeps renewing the lease while it leads") {
    FakeApiServer(setOf("token-a")).use { server ->
      withServiceAccounts("token-a") { accounts ->
        listOf(KubernetesLeaderElection(testConfig(server, accounts.getValue("token-a"), "pod-a"), http)).use { (a) ->
          a.start()
          val first = server.spec("renewTime")
          eventually { server.spec("renewTime") != first }
          sleep(1500.milliseconds)
          // longer than the lease duration, and still the leader, of the same lease term
          assert(a.isLeader())
          assert(server.holder() == "pod-a")
          assert(server.transitions() == 0)
        }
      }
    }
  }

  test("of two instances, one leads") {
    FakeApiServer(setOf("token-a", "token-b")).use { server ->
      withServiceAccounts("token-a", "token-b") { accounts ->
        listOf(
          KubernetesLeaderElection(testConfig(server, accounts.getValue("token-a"), "pod-a"), http),
          KubernetesLeaderElection(testConfig(server, accounts.getValue("token-b"), "pod-b"), http),
        ).use { instances ->
          instances.forEach { it.start() }
          sleep(1500.milliseconds)
          assert(instances.count { it.isLeader() } == 1)
          val leader = instances.single { it.isLeader() }
          assert(server.holder() == leader.identity)
          assert(instances.all { it.holderIdentity == leader.identity })
        }
      }
    }
  }

  test("the leader releases the lease on shutdown, and another takes over at once") {
    FakeApiServer(setOf("token-a", "token-b")).use { server ->
      withServiceAccounts("token-a", "token-b") { accounts ->
        val a = KubernetesLeaderElection(testConfig(server, accounts.getValue("token-a"), "pod-a"), http)
        val b = KubernetesLeaderElection(testConfig(server, accounts.getValue("token-b"), "pod-b"), http)
        listOf(a, b).use {
          a.start()
          b.start()
          assert(a.isLeader() && !b.isLeader())
          a.shutdown()
          assert(!a.isLeader())
          val released = TimeSource.Monotonic.markNow()
          eventually { b.isLeader() }
          // well within the lease duration: the released lease is free at once
          assert(released.elapsedNow() < 1.seconds)
          assert(server.holder() == "pod-b")
          assert(server.transitions() == 1)
        }
      }
    }
  }

  test("a leader that can't renew gives up leadership, and another takes over once the lease expires") {
    FakeApiServer(setOf("token-a", "token-b")).use { server ->
      withServiceAccounts("token-a", "token-b") { accounts ->
        val a = KubernetesLeaderElection(testConfig(server, accounts.getValue("token-a"), "pod-a"), http)
        val b = KubernetesLeaderElection(testConfig(server, accounts.getValue("token-b"), "pod-b"), http)
        listOf(a, b).use {
          a.start()
          b.start()
          assert(a.isLeader())
          // pod-a loses its connection to the API server
          server.unavailableTo += "token-a"
          val cutOff = TimeSource.Monotonic.markNow()
          // pod-a gives up after the renew deadline, and pod-b takes over once the lease expires, a lease duration
          // after it last saw pod-a renew it, so the two never lead at once
          var bothLed = false
          eventually {
            if (a.isLeader() && b.isLeader()) bothLed = true
            b.isLeader()
          }
          assert(!bothLed)
          assert(!a.isLeader())
          // pod-b last saw pod-a renew at most a retry period or so before the cut-off
          assert(cutOff.elapsedNow() >= 700.milliseconds)
          assert(server.holder() == "pod-b")
          // pod-a is back, and follows
          server.unavailableTo -= "token-a"
          sleep(500.milliseconds)
          assert(!a.isLeader() && b.isLeader())
        }
      }
    }
  }

  test("a leader whose requests hang gives up leadership at the renew deadline, before the lease expires") {
    FakeApiServer(setOf("token-a", "token-b")).use { server ->
      withServiceAccounts("token-a", "token-b") { accounts ->
        // a request timeout longer than the lease: the deadline can't wait for a hanging request to fail
        val config = { token: String, identity: String ->
          testConfig(server, accounts.getValue(token), identity).copy(requestTimeout = 3.seconds)
        }
        val a = KubernetesLeaderElection(config("token-a", "pod-a"), http)
        val b = KubernetesLeaderElection(config("token-b", "pod-b"), http)
        listOf(a, b).use {
          a.start()
          b.start()
          assert(a.isLeader())
          server.stalledFor += "token-a"
          val cutOff = TimeSource.Monotonic.markNow()
          eventually { !a.isLeader() }
          // the renew deadline, from its last renewal, which was at most a retry period or so before the cut-off
          assert(cutOff.elapsedNow() < 800.milliseconds)
          var bothLed = false
          eventually {
            if (a.isLeader() && b.isLeader()) bothLed = true
            b.isLeader()
          }
          assert(!bothLed)
          assert(server.holder() == "pod-b")
        }
      }
    }
  }

  test("a clock that jumps doesn't expire another holder's lease early") {
    // pod-b's clock jumps an hour ahead once it has seen pod-a's lease
    val offset = AtomicReference(Duration.ZERO)
    val jumpy = object : Clock {
      override fun now() = Clock.System.now() + offset.get()
    }
    FakeApiServer(setOf("token-a", "token-b")).use { server ->
      withServiceAccounts("token-a", "token-b") { accounts ->
        val a = KubernetesLeaderElection(testConfig(server, accounts.getValue("token-a"), "pod-a"), http)
        val b = KubernetesLeaderElection(testConfig(server, accounts.getValue("token-b"), "pod-b"), http, jumpy)
        listOf(a, b).use {
          a.start()
          b.start()
          assert(a.isLeader() && !b.isLeader())
          // pod-a stops renewing, without releasing, so pod-b must wait out the lease
          server.unavailableTo += "token-a"
          offset.set(1.hours)
          val cutOff = TimeSource.Monotonic.markNow()
          eventually { b.isLeader() }
          // a lease duration after pod-b last saw pod-a renew, not at once
          assert(cutOff.elapsedNow() >= 700.milliseconds)
        }
      }
    }
  }

  test("takes over a lease that another holder stopped renewing, once it expires") {
    FakeApiServer(setOf("token-a")).use { server ->
      server.putLease(NAMESPACE, LEASE, buildJsonObject {
        put("holderIdentity", "gone")
        put("leaseDurationSeconds", 1)
        put("renewTime", "2026-01-01T00:00:00.000000Z")
        put("leaseTransitions", 4)
      })
      withServiceAccounts("token-a") { accounts ->
        listOf(KubernetesLeaderElection(testConfig(server, accounts.getValue("token-a"), "pod-a"), http)).use { (a) ->
          a.start()
          // its expiry is timed from when this instance saw it, not from its renew time, so clocks needn't agree
          assert(!a.isLeader())
          assert(a.holderIdentity == "gone")
          eventually { a.isLeader() }
          assert(server.holder() == "pod-a")
          assert(server.transitions() == 5)
        }
      }
    }
  }

  test("the fencing token is the lease's transitions, stable across renewals") {
    FakeApiServer(setOf("token-a")).use { server ->
      withServiceAccounts("token-a") { accounts ->
        listOf(KubernetesLeaderElection(testConfig(server, accounts.getValue("token-a"), "pod-a"), http)).use { (a) ->
          a.start()
          assert(a.fencingToken == 0L)
          val first = server.spec("renewTime")
          eventually { server.spec("renewTime") != first }
          sleep(1500.milliseconds)
          assert(a.fencingToken == 0L)
          assert(server.transitions() == 0)
        }
      }
    }
  }

  test("the fencing token increases with each takeover, and is null while not leading") {
    FakeApiServer(setOf("token-a", "token-b", "token-c")).use { server ->
      withServiceAccounts("token-a", "token-b", "token-c") { accounts ->
        val a = KubernetesLeaderElection(testConfig(server, accounts.getValue("token-a"), "pod-a"), http)
        val b = KubernetesLeaderElection(testConfig(server, accounts.getValue("token-b"), "pod-b"), http)
        val c = KubernetesLeaderElection(testConfig(server, accounts.getValue("token-c"), "pod-c"), http)
        listOf(a, b, c).use {
          a.start()
          b.start()
          assert(a.fencingToken == 0L && b.fencingToken == null)
          // pod-a releases the lease, and pod-b takes over
          a.shutdown()
          assert(a.fencingToken == null)
          eventually { b.isLeader() }
          assert(b.fencingToken == 1L)
          c.start()
          assert(c.fencingToken == null)
          // pod-b stops renewing, and pod-c takes over once the lease expires
          server.unavailableTo += "token-b"
          eventually { c.isLeader() }
          assert(c.fencingToken == 2L)
          assert(b.fencingToken == null)
          assert(server.transitions() == 2)
        }
      }
    }
  }

  test("a leader that stepped down takes the lease again for a new lease term, though it still holds it") {
    FakeApiServer(setOf("token-a")).use { server ->
      withServiceAccounts("token-a") { accounts ->
        val config = testConfig(server, accounts.getValue("token-a"), "pod-a").copy(requestTimeout = 3.seconds)
        listOf(KubernetesLeaderElection(config, http)).use { (a) ->
          a.start()
          assert(a.fencingToken == 0L)
          // its requests hang past the renew deadline, so it steps down, though no other instance takes the lease
          server.stalledFor += "token-a"
          eventually { !a.isLeader() }
          assert(a.fencingToken == null)
          server.stalledFor -= "token-a"
          eventually { a.isLeader() }
          // a work run of its first lease term might still be running: its token is lower than this lease term's
          assert(server.holder() == "pod-a")
          assert(a.fencingToken == 1L)
          assert(server.transitions() == 1)
        }
      }
    }
  }

  test("a fenced write refuses a paused leader's late write once a later leader has written") {
    FakeApiServer(setOf("token-a", "token-b")).use { server ->
      withServiceAccounts("token-a", "token-b") { accounts ->
        // the store's fence: the highest token that has written, as a database row's fence column would hold it
        val fence = AtomicLong(-1)
        fun write(token: Long): Boolean {
          while (true) {
            val seen = fence.get()
            if (token < seen) return false
            if (fence.compareAndSet(seen, token)) return true
          }
        }
        val a = KubernetesLeaderElection(testConfig(server, accounts.getValue("token-a"), "pod-a"), http)
        val b = KubernetesLeaderElection(testConfig(server, accounts.getValue("token-b"), "pod-b"), http)
        listOf(a, b).use {
          a.start()
          b.start()
          // pod-a's work reads its token as it starts, and then pauses, longer than the lease
          val paused = a.fencingToken!!
          assert(write(paused))
          server.unavailableTo += "token-a"
          eventually { b.isLeader() }
          assert(write(b.fencingToken!!))
          // pod-a's work carries on, as though it still led: its write is refused
          assert(!write(paused))
        }
      }
    }
  }

  test("dates the lease by its clock, in the API server's MicroTime format") {
    val now = Instant.parse("2026-11-18T12:34:56.789Z")
    val clock = object : Clock {
      override fun now() = now
    }
    FakeApiServer(setOf("token-a")).use { server ->
      withServiceAccounts("token-a") { accounts ->
        listOf(KubernetesLeaderElection(testConfig(server, accounts.getValue("token-a"), "pod-a"), http, clock)).use { (a) ->
          a.start()
          assert(server.spec("acquireTime") == "2026-11-18T12:34:56.789000Z")
          assert(server.spec("renewTime") == "2026-11-18T12:34:56.789000Z")
        }
      }
    }
  }

  test("a request that times out is a failure to retry, not the election's cancellation") {
    FakeApiServer(setOf("token-a")).use { server ->
      server.stalledFor += "token-a"
      withServiceAccounts("token-a") { accounts ->
        val config = testConfig(server, accounts.getValue("token-a"), "pod-a").copy(requestTimeout = 200.milliseconds)
        listOf(KubernetesLeaderElection(config, http)).use { (a) ->
          // the start's first attempt times out, and the start carries on
          a.start()
          assert(!a.isLeader())
          server.stalledFor -= "token-a"
          eventually { a.isLeader() }
        }
      }
    }
  }

  test("fails the start when the API server refuses access") {
    FakeApiServer(setOf("token-a")).use { server ->
      server.forbidden += "token-a"
      withServiceAccounts("token-a") { accounts ->
        val a = KubernetesLeaderElection(testConfig(server, accounts.getValue("token-a"), "pod-a"), http)
        val failure = runCatching { a.start() }.exceptionOrNull() as? KubernetesApiException
        assert(failure?.status == 403)
        assert("leases is forbidden" in failure?.message.orEmpty())
        assert(!a.isLeader())
      }
    }
  }

  test("starts, and keeps trying, when the API server is unavailable") {
    FakeApiServer(setOf("token-a")).use { server ->
      server.unavailableTo += "token-a"
      withServiceAccounts("token-a") { accounts ->
        listOf(KubernetesLeaderElection(testConfig(server, accounts.getValue("token-a"), "pod-a"), http)).use { (a) ->
          a.start()
          assert(!a.isLeader())
          server.unavailableTo -= "token-a"
          eventually { a.isLeader() }
        }
      }
    }
  }

  test("assumeLeader leads without an election") {
    val a = KubernetesLeaderElection(
      KubernetesLeaderElectionConfig(leaseName = LEASE, assumeLeader = true),
      http,
      environment = mapOf("HOSTNAME" to "local"),
    )
    a.start()
    assert(a.isLeader())
    assert(a.fencingToken == 0L)
    a.shutdown()
    assert(a.identity == "local")
  }

  test("the identity is the pod's name, from HOSTNAME, by default") {
    withServiceAccounts("token-a") { accounts ->
      val config = KubernetesLeaderElectionConfig(leaseName = LEASE, serviceAccountDir = accounts.getValue("token-a").toString())
      val env = mapOf("HOSTNAME" to "pod-7", "KUBERNETES_SERVICE_HOST" to "10.0.0.1", "KUBERNETES_SERVICE_PORT" to "443")
      assert(KubernetesLeaderElection(config, http, environment = env).identity == "pod-7")
    }
  }

  test("without HOSTNAME, the identity is the host's name") {
    val election = KubernetesLeaderElection(KubernetesLeaderElectionConfig(LEASE, assumeLeader = true), http, environment = emptyMap())
    assert(election.identity == java.net.InetAddress.getLocalHost().hostName)
  }

  test("without an API server or namespace, says what to configure") {
    val config = KubernetesLeaderElectionConfig(leaseName = LEASE, serviceAccountDir = "/nonexistent")
    val failure = runCatching { KubernetesLeaderElection(config, http, environment = mapOf("HOSTNAME" to "pod")) }
    assert("leader.kubernetes.namespace" in failure.exceptionOrNull()?.message.orEmpty())
  }

  test("the config rejects timings that could elect two leaders") {
    assert(runCatching { KubernetesLeaderElectionConfig(LEASE, renewDeadline = 15.seconds) }.isFailure)
    assert(runCatching { KubernetesLeaderElectionConfig(LEASE, retryPeriod = 10.seconds) }.isFailure)
  }

  test("starts in the Infrastructure phase, before the services that ask it") {
    val election = KubernetesLeaderElection(
      KubernetesLeaderElectionConfig(LEASE, assumeLeader = true), http, environment = mapOf("HOSTNAME" to "pod"),
    )
    val app = object : AppService {}
    assert(election.ordering().phase == DefaultPhase.Infrastructure)
    assert(startPlan(setOf(app, election), tiebreaker = StartOrder.Tiebreaker.Shuffled()) == listOf(election, app))
  }
}
