@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.clock

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.koin.bootableModule as koinBootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.clock.koin.clockModule as koinClockModule
import com.github.rocketraman.bootable.leader.AlwaysLeaderModule
import com.github.rocketraman.bootable.leader.LeaderElectionModule
import com.github.rocketraman.bootable.periodic.PeriodicModule
import com.github.rocketraman.bootable.periodic.PeriodicServices
import com.github.rocketraman.bootable.periodic.koin.periodicModule as koinPeriodicModule
import com.github.rocketraman.bootable.periodic.periodicModule
import com.github.rocketraman.bootable.telemetry.otel.NoopOpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryApiModule
import com.github.rocketraman.bootable.test.TestClock
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createDynamicGraph
import dev.zacsweers.metro.createGraph
import java.time.Clock as JClock
import kotlin.time.Duration.Companion.hours
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.direct
import org.kodein.di.inara.DIModule
import org.kodein.di.instance
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.toJavaInstant

sealed interface TestScope

@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class])
interface ClockGraph {
  val clock: Clock
  val javaClock: JClock
  val periodicServices: PeriodicServices
}

/** A test's clock, in place of the graph's. */
@BindingContainer
class TestClockBindings(private val clock: TestClock) {
  @Provides fun clock(): Clock = clock
}

/** An application's module, composed as an Inara application composes it, with its clock. */
@DIModule(composed = true)
interface ClockAppModule : ClockModule, LeaderElectionModule, OpenTelemetryApiModule, PeriodicModule

// the Kodein and Inara flavors' clockModule have the same signature: a reference of each type picks one
private val kodeinClockModule: (Clock) -> DI.Module = ::clockModule
private val inaraClockModule: (Clock) -> ClockModule = ::clockModule

private fun clockAppModule(clock: Clock = Clock.System) = ClockAppModule(
  clockModule = inaraClockModule(clock),
  leaderElectionModule = AlwaysLeaderModule,
  openTelemetryApiModule = NoopOpenTelemetryModule,
  periodicModule = periodicModule(),
)

/** Checks [javaClock] wraps [clock], by moving [clock]. */
private fun checkWraps(javaClock: JClock, clock: TestClock) {
  assert(javaClock.instant() == clock.now().toJavaInstant())
  clock.advanceBy(1.hours)
  assert(javaClock.instant() == clock.now().toJavaInstant())
}

val ClockWiringTest by testSuite {
  testSuite("kodein") {
    test("binds the system clock, as both clocks, for the modules that take a clock") {
      val di = DI {
        import(bootableModule)
        import(kodeinClockModule(Clock.System))
        import(periodicModule)
      }.direct
      assert(di.instance<Clock>() == Clock.System)
      assert(di.instance<JClock>() == JClock.systemUTC())
      assert(di.instance<PeriodicServices>().clock == Clock.System)
    }

    test("a test's clock, in place of the module's, sets both clocks") {
      val testClock = TestClock()
      val di = DI {
        import(bootableModule)
        import(kodeinClockModule(Clock.System))
        import(periodicModule)
        bindSingleton<Clock>(overrides = true) { testClock }
      }.direct
      assert(di.instance<Clock>() === testClock)
      assert(di.instance<PeriodicServices>().clock === testClock)
      checkWraps(di.instance(), testClock)
    }

    test("binds the clock it is given") {
      val testClock = TestClock()
      val di = DI { import(kodeinClockModule(testClock)) }.direct
      assert(di.instance<Clock>() === testClock)
      checkWraps(di.instance(), testClock)
    }
  }

  testSuite("koin") {
    test("defines the system clock, as both clocks, for the modules that take a clock") {
      val koin = koinApplication { modules(koinBootableModule(), koinClockModule(), koinPeriodicModule()) }.koin
      try {
        assert(koin.get<Clock>() == Clock.System)
        assert(koin.get<JClock>() == JClock.systemUTC())
        assert(koin.get<PeriodicServices>().clock == Clock.System)
      } finally {
        koin.close()
      }
    }

    test("a test's clock, defined after the module's, sets both clocks") {
      val testClock = TestClock()
      val koin = koinApplication {
        modules(koinBootableModule(), koinClockModule(), koinPeriodicModule(), module { single<Clock> { testClock } })
      }.koin
      try {
        assert(koin.get<Clock>() === testClock)
        assert(koin.get<PeriodicServices>().clock === testClock)
        checkWraps(koin.get(), testClock)
      } finally {
        koin.close()
      }
    }

    test("defines the clock it is given") {
      val testClock = TestClock()
      val koin = koinApplication { modules(koinClockModule(testClock)) }.koin
      try {
        assert(koin.get<Clock>() === testClock)
        checkWraps(koin.get(), testClock)
      } finally {
        koin.close()
      }
    }
  }

  testSuite("metro") {
    test("provides the system clock, as both clocks, for the bindings that take a clock") {
      val graph = createGraph<ClockGraph>()
      assert(graph.clock == Clock.System)
      assert(graph.javaClock == JClock.systemUTC())
      assert(graph.periodicServices.clock == Clock.System)
    }

    test("a test's clock, in a dynamic graph, sets both clocks") {
      val testClock = TestClock()
      val graph = createDynamicGraph<ClockGraph>(TestClockBindings(testClock))
      assert(graph.clock === testClock)
      assert(graph.periodicServices.clock === testClock)
      checkWraps(graph.javaClock, testClock)
    }
  }

  testSuite("inara") {
    test("provides the system clock, as both clocks") {
      val module = clockAppModule()
      assert(module.clock == Clock.System)
      assert(module.javaClock == JClock.systemUTC())
      assert(module.periodicServices.clock == Clock.System)
    }

    test("provides the clock it is given, as both clocks") {
      val testClock = TestClock()
      val module = clockAppModule(testClock)
      assert(module.clock === testClock)
      assert(module.periodicServices.clock === testClock)
      checkWraps(module.javaClock, testClock)
    }
  }
}
