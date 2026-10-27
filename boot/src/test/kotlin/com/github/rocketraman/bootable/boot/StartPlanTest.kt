package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.boot.DefaultPhase.Application
import com.github.rocketraman.bootable.boot.DefaultPhase.Infrastructure
import com.github.rocketraman.bootable.boot.DefaultPhase.Initialization
import com.github.rocketraman.bootable.boot.DefaultPhase.Serving
import de.infix.testBalloon.framework.core.testSuite

private class Service(private val name: String, private val ordering: Ordering = Ordering()) : AppService {
  override fun name() = name
  override fun ordering() = ordering
  override fun toString() = name
}

private val Ready = milestone("ready")
private val Migrated = milestone("migrated")

private fun Collection<AppService>.plan(
  startOrder: StartOrder = DefaultStartOrder,
  tiebreaker: StartOrder.Tiebreaker = StartOrder.Tiebreaker.ByName,
) = startPlan(this, startOrder, tiebreaker).map { it.name() }

private fun Collection<AppService>.problems(startOrder: StartOrder = DefaultStartOrder): List<String> =
  (runCatching { startPlan(this, startOrder) }.exceptionOrNull() as StartOrderException).problems

private enum class AppPhase : Phase { Secrets }

private object AppStartOrder : StartOrder by DefaultStartOrder {
  override val phases = listOf(AppPhase.Secrets) + DefaultStartOrder.phases
}

val StartPlanTest by testSuite {
  testSuite("phases") {
    test("start in order, with the default phase for a service that names none") {
      val services = setOf(
        Service("server", Ordering(Serving)),
        Service("app"),
        Service("migrations", Ordering(Initialization)),
        Service("pool", Ordering(Infrastructure)),
      )
      assert(services.plan() == listOf("pool", "migrations", "app", "server"))
    }

    test("can be added by an app, ahead of the default phases") {
      val services = setOf(Service("pool", Ordering(Infrastructure)), Service("secrets", Ordering(AppPhase.Secrets)))
      assert(services.plan(AppStartOrder) == listOf("secrets", "pool"))
    }

    test("can be replaced, mapping the default phases onto the app's own") {
      val ours = object : StartOrder {
        override val phases: List<Phase> = listOf(AppPhase.Secrets, Infrastructure)
        override val defaultPhase = Infrastructure
        override fun orderingOf(service: AppService) =
          service.ordering().let { if (it.phase == Serving) it.copy(phase = AppPhase.Secrets) else it }
      }
      assert(setOf(Service("app"), Service("server", Ordering(Serving))).plan(ours) == listOf("server", "app"))
    }

    test("a phase the start order doesn't know fails the plan") {
      assert(
        setOf(Service("secrets", Ordering(AppPhase.Secrets))).problems() == listOf(
          "secrets is in phase Secrets, which is not one of the start order's phases: " +
            "[Infrastructure, Initialization, Application, Serving]. Pass Bootable a StartOrder that includes Secrets."
        )
      )
    }

    test("a start order that repeats a phase, or lacks its default phase, fails the plan") {
      val bad = object : StartOrder {
        override val phases = listOf(Infrastructure, Serving, Infrastructure)
        override val defaultPhase = Application
      }
      assert(
        emptySet<AppService>().problems(bad) == listOf(
          "The start order repeats the phase(s) Infrastructure: [Infrastructure, Serving, Infrastructure]",
          "The start order's default phase Application is not one of its phases: " +
            "[Infrastructure, Serving, Infrastructure]",
        )
      )
    }
  }

  testSuite("milestones") {
    test("order services within a phase") {
      val services = setOf(
        Service("a-verify", Ordering(Initialization, after = setOf(Migrated))),
        Service("b-migrate", Ordering(Initialization, provides = setOf(Migrated), requires = setOf(Ready))),
        Service("c-pool", Ordering(Initialization, provides = setOf(Ready))),
      )
      assert(services.plan() == listOf("c-pool", "b-migrate", "a-verify"))
    }

    test("wait for every provider") {
      val services = setOf(
        Service("a", Ordering(after = setOf(Ready))),
        Service("b", Ordering(provides = setOf(Ready))),
        Service("c", Ordering(provides = setOf(Ready))),
      )
      assert(services.plan() == listOf("b", "c", "a"))
    }

    test("provided in an earlier phase are already reached") {
      val services = setOf(
        Service("a-verify", Ordering(Application, requires = setOf(Migrated))),
        Service("b-migrate", Ordering(Initialization, provides = setOf(Migrated))),
      )
      assert(services.plan() == listOf("b-migrate", "a-verify"))
    }

    test("'after' one nobody provides is reached at once") {
      assert(setOf(Service("verify", Ordering(after = setOf(Migrated)))).plan() == listOf("verify"))
    }

    test("'requires' one nobody provides fails the plan") {
      assert(
        setOf(Service("verify", Ordering(requires = setOf(Migrated)))).problems() == listOf(
          "verify requires milestone 'migrated', but no service provides it. Add a service that provides it, or use " +
            "'after' if it is optional."
        )
      )
    }

    test("provided in a later phase fails the plan") {
      val services = setOf(
        Service("verify", Ordering(Initialization, after = setOf(Migrated))),
        Service("migrate", Ordering(Application, provides = setOf(Migrated))),
      )
      assert(
        services.problems() == listOf(
          "verify (Initialization) must start after 'migrated', but its provider migrate is in Application, which " +
            "starts later"
        )
      )
    }

    test("in a cycle fail the plan, naming the cycle") {
      val services = setOf(
        Service("a", Ordering(provides = setOf(Ready), after = setOf(Migrated))),
        Service("b", Ordering(provides = setOf(Migrated), requires = setOf(Ready))),
        Service("c", Ordering(after = setOf(Migrated))),
        Service("d"),
      )
      assert(
        services.problems() == listOf(
          "Start-order cycle in phase Application: a (after 'migrated') -> b (requires 'ready') -> a"
        )
      )
    }

    test("a service waiting for its own milestone is a cycle") {
      assert(
        setOf(Service("a", Ordering(provides = setOf(Ready), after = setOf(Ready)))).problems() == listOf(
          "Start-order cycle in phase Application: a (after 'ready') -> a"
        )
      )
    }

    test("made by name are equal") {
      assert(milestone("ready") == Ready)
    }
  }

  testSuite("ties") {
    val services = ('a'..'h').map { Service("$it") }.shuffled().toSet()

    test("are broken by name by default") {
      assert(services.plan() == ('a'..'h').map { "$it" })
    }

    test("are shuffled reproducibly from the seed, whatever order the services came in") {
      val shuffled = services.plan(tiebreaker = StartOrder.Tiebreaker.Shuffled(42))
      assert(services.reversed().plan(tiebreaker = StartOrder.Tiebreaker.Shuffled(42)) == shuffled)
      assert(shuffled.sorted() == ('a'..'h').map { "$it" })
    }

    test("when shuffled, still respect phases and milestones") {
      val ordered = setOf(
        Service("server", Ordering(Serving)),
        Service("verify", Ordering(after = setOf(Migrated))),
        Service("migrate", Ordering(provides = setOf(Migrated))),
      ) + services
      repeat(20) { seed ->
        val plan = ordered.plan(tiebreaker = StartOrder.Tiebreaker.Shuffled(seed.toLong()))
        assert(plan.last() == "server")
        assert(plan.indexOf("migrate") < plan.indexOf("verify"))
      }
    }
  }

  test("an app can read orderings from elsewhere") {
    val overrides = object : StartOrder by DefaultStartOrder {
      override fun orderingOf(service: AppService) =
        if (service.name() == "legacy") Ordering(Infrastructure) else service.ordering()
    }
    assert(setOf(Service("app"), Service("legacy")).plan(overrides) == listOf("legacy", "app"))
  }

  test("the plan is described by phase, leaving out empty phases") {
    val plan = phasedStartPlan(
      setOf(Service("pool", Ordering(Infrastructure)), Service("server", Ordering(Serving)), Service("app")),
      DefaultStartOrder,
      StartOrder.Tiebreaker.ByName,
    )
    assert(plan.describe() == "Infrastructure[pool] Application[app] Serving[server]")
  }
}
