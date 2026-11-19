package com.github.rocketraman.bootable.leader.k8s

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.startPlan
import com.github.rocketraman.bootable.client.http.ktor.JavaClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientDefaults
import com.github.rocketraman.bootable.client.http.ktor.KtorClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientService
import com.github.rocketraman.bootable.client.http.ktor.ktorClientModule
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import org.kodein.di.bindSingleton
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.leader.LeaderElection
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance
import com.github.rocketraman.bootable.boot.AppService
import kotlin.time.Duration.Companion.seconds

sealed interface TestScope

@DependencyGraph(TestScope::class, additionalScopes = [BootScope::class], bindingContainers = [EngineBindings::class])
interface TestGraph : BootableGraph {
  val leaderElection: LeaderElection
  val ktorClientDefaults: Set<KtorClientDefaults>
}

// named rather than contributed: a @ContributesTo in test code trips the power-assert bug KT-83931 on the hint file
// Metro generates for it
@BindingContainer
object EngineBindings {
  @Provides fun engine(): KtorClientEngine = JavaClientEngine()
}

/** The election stops, releasing its Lease, before the HTTP clients close. */
private fun checkOrder(services: Set<AppService>) {
  val plan = startPlan(services, tiebreaker = StartOrder.Tiebreaker.Shuffled()).map { it::class }
  assert(plan.indexOf(KtorClientService::class) < plan.indexOf(KubernetesLeaderElection::class))
}

/** Config comes from src/test/resources/application.conf, as it would in an application. */
val KubernetesLeaderElectionWiringTest by testSuite {
  testSuite("leader election config decoded by") {
    test("hoplite") {
      val config = hopliteConfigBinder(report = false).config<KubernetesLeaderElectionConfig>("leader.kubernetes")
      assert(
        config == KubernetesLeaderElectionConfig(
          leaseName = "wiring-test-leader",
          namespace = "apps",
          identity = "pod-1",
          leaseDuration = 30.seconds,
          renewDeadline = 20.seconds,
          assumeLeader = true,
        )
      )
    }

    test("hoplite in strict mode") {
      hopliteConfigBinder(report = false, strict = true).config<KubernetesLeaderElectionConfig>("leader.kubernetes")
    }

    test("kodein") {
      val di = DI {
        import(bootableModule)
        import(hopliteConfigModule)
        import(ktorClientModule)
        import(kubernetesLeaderElectionModule)
        bindSingleton<KtorClientEngine> { JavaClientEngine() }
      }.direct
      val election = di.instance<LeaderElection>() as KubernetesLeaderElection
      assert(election.identity == "pod-1")
      assert(election in di.instance<Set<AppService>>())
      assert("kubernetes" in di.instance<KtorClientService>().names)
      // the election contributes the kubernetes client's defaults: none here, with assumeLeader
      assert(di.instance<Set<KtorClientDefaults>>().single().clients().isEmpty())
      checkOrder(di.instance())
      election.start()
      assert(election.isLeader())
      election.shutdown()
    }

    test("metro") {
      val graph = createGraph<TestGraph>()
      val election = graph.leaderElection as KubernetesLeaderElection
      assert(election.identity == "pod-1")
      assert(election in graph.appServices)
      checkOrder(graph.appServices)
      assert(graph.ktorClientDefaults.single().clients().isEmpty())
    }
  }
}
