@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.periodic

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.koin.bootableModule as koinBootableModule
import com.github.rocketraman.bootable.boot.setOfAppServices
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule as koinHopliteConfigModule
import com.github.rocketraman.bootable.leader.AlwaysLeaderModule
import com.github.rocketraman.bootable.leader.LeaderElection
import com.github.rocketraman.bootable.leader.LeaderElectionModule
import com.github.rocketraman.bootable.periodic.koin.periodicModule as koinPeriodicModule
import com.github.rocketraman.bootable.telemetry.otel.NoopOpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryApiModule
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
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.instance
import org.kodein.di.singleton
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

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

/** An application's module, composed as an Inara application composes it, with the loop the wiring creates. */
@DIModule(composed = true)
interface InaraWiringModule :
  ConfigModule, BootableModule, LeaderElectionModule, OpenTelemetryApiModule, PeriodicModule {
  val counter: Counter
  @AppServices val counterLoop: PeriodicAppService
}

private fun inaraWiringModule(leaderElectionModule: LeaderElectionModule) = InaraWiringModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  leaderElectionModule = leaderElectionModule,
  openTelemetryApiModule = NoopOpenTelemetryModule,
  periodicModule = periodicModule(),
  counter = Binding.single { Counter() },
  counterLoop = Binding.single {
    periodicServices.create("counter", configBinder.config("jobs.counter")) { counter.runs.incrementAndGet() }
  },
)

/** Starts the loop, checks it runs only once its instance leads, and stops it. */
private suspend fun checkLeaderGatedLoop(services: Set<AppService>, election: TestLeaderElection, counter: Counter) {
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

    test("koin, gated on the LeaderElection definition") {
      val koin = koinApplication {
        modules(
          koinBootableModule(),
          koinHopliteConfigModule(),
          koinPeriodicModule(),
          module {
            single { Counter() }
            single<LeaderElection> { TestLeaderElection(false) }
            single<AppService>(named("counter")) {
              get<PeriodicServices>().create("counter", get<ConfigBinder>().config("jobs.counter")) {
                get<Counter>().runs.incrementAndGet()
              }
            }
          },
        )
      }.koin
      try {
        checkLeaderGatedLoop(koin.getAll<AppService>().toSet(), koin.get<LeaderElection>() as TestLeaderElection, koin.get())
      } finally {
        koin.close()
      }
    }

    test("koin, ungated without a LeaderElection definition") {
      val koin = koinApplication { modules(koinBootableModule(), koinPeriodicModule()) }.koin
      try {
        assert(koin.get<PeriodicServices>().gate.isOpen())
      } finally {
        koin.close()
      }
    }

    test("metro, gated on the graph's LeaderElection") {
      val graph = createGraph<TestGraph>()
      checkLeaderGatedLoop(graph.appServices, graph.leaderElection, graph.counter)
    }

    test("metro, not traced without an OpenTelemetry in the graph") {
      assert(createGraph<TestGraph>().periodicServices.interceptors.isEmpty())
    }

    test("inara, gated on the LeaderElectionModule's election") {
      val module = inaraWiringModule(LeaderElectionModule(leaderElection = Binding.value(TestLeaderElection(false))))
      checkLeaderGatedLoop(module.setOfAppServices(), module.leaderElection as TestLeaderElection, module.counter)
    }

    test("inara, ungated with the AlwaysLeaderModule") {
      assert(inaraWiringModule(AlwaysLeaderModule).periodicServices.gate.isOpen())
    }

    test("inara, not traced with the NoopOpenTelemetryModule") {
      assert(inaraWiringModule(AlwaysLeaderModule).periodicServices.interceptors.isEmpty())
    }
  }
}
