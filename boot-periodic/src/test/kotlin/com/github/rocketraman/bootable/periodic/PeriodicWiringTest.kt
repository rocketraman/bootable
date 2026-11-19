package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.leader.LeaderElection
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.flow.MutableStateFlow
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

/** A leader election a test controls. */
class TestLeaderElection(leading: Boolean) : LeaderElection {
  override val leadership = MutableStateFlow(leading)
  override val fencingToken get() = if (leadership.value) 1L else null
}

/** Counts the runs of the loop the wiring creates. */
class Counter {
  val runs = AtomicInteger()
}

sealed interface TestScope

@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class], bindingContainers = [TestBindings::class])
interface TestGraph : BootableGraph {
  val periodicServices: PeriodicServices
  val counter: Counter
  val leaderElection: TestLeaderElection
}

// named rather than contributed: a @ContributesTo in test code trips the power-assert bug KT-83931 on the hint file
// Metro generates for it
@BindingContainer
object TestBindings {
  @Provides @SingleIn(TestScope::class) fun counter(): Counter = Counter()

  @Provides @SingleIn(TestScope::class) fun testLeaderElection(): TestLeaderElection = TestLeaderElection(false)

  @Provides fun leaderElection(election: TestLeaderElection): LeaderElection = election

  @Provides @IntoSet
  fun counterLoop(periodic: PeriodicServices, binder: ConfigBinder, counter: Counter): AppService =
    periodic.create("counter", binder.config("jobs.counter")) { counter.runs.incrementAndGet() }
}

/** Starts the loop, checks it runs only once its instance leads, and stops it. */
private fun checkLeaderGatedLoop(services: Set<AppService>, election: TestLeaderElection, counter: Counter) {
  val loop = services.filterIsInstance<PeriodicAppService>().single()
  loop.startService()
  try {
    sleep(50.milliseconds)
    assert(counter.runs.get() == 0)
    election.leadership.value = true
    eventually { counter.runs.get() >= 2 }
  } finally {
    loop.shutdown()
  }
}

/** Config comes from src/test/resources/application.conf, as it would in an application. */
val PeriodicWiringTest by testSuite {
  testSuite("periodic config decoded by") {
    test("hoplite") {
      val config = hopliteConfigBinder(report = false).config<PeriodicConfig>("jobs.counter")
      assert(
        config == PeriodicConfig(
          interval = 10.milliseconds,
          maxConsecutiveFailures = 5,
          onFailureLimit = FailureLimitAction.Shutdown,
        )
      )
    }

    test("hoplite, as all defaults when the section is absent") {
      assert(hopliteConfigBinder(report = false).config<PeriodicConfig>("absent.loop") == PeriodicConfig())
    }

    test("kodein, gated on the LeaderElection binding") {
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(periodicModule)
        bindSingleton { Counter() }
        bindSingleton<LeaderElection> { TestLeaderElection(false) }
        inBindSet<AppService> {
          add {
            singleton {
              instance<PeriodicServices>().create("counter", instance<ConfigBinder>().config("jobs.counter")) {
                instance<Counter>().runs.incrementAndGet()
              }
            }
          }
        }
      }.direct
      checkLeaderGatedLoop(di.instance(), di.instance<LeaderElection>() as TestLeaderElection, di.instance())
    }

    test("kodein, ungated without a LeaderElection binding") {
      val di = DI {
        import(bootableModule)
        import(periodicModule)
      }.direct
      assert(di.instance<PeriodicServices>().gate.isOpen())
    }

    test("metro, gated on the graph's LeaderElection") {
      val graph = createGraph<TestGraph>()
      checkLeaderGatedLoop(graph.appServices, graph.leaderElection, graph.counter)
    }
  }
}
