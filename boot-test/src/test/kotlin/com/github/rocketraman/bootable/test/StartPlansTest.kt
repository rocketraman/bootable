package com.github.rocketraman.bootable.test

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.boot.Phase
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.startPlan
import de.infix.testBalloon.framework.core.testSuite
import kotlin.random.Random

private class Named(override val name: String, private val phase: Phase) : AppService {
  override fun ordering() = Ordering(phase)
}

val StartPlansTest by testSuite {
  val services = (1..6).map { Named("s$it", DefaultPhase.Application) }.toSet() + Named("pool", DefaultPhase.Infrastructure)

  test("checks each shuffled plan, in which the phases still hold") {
    var rounds = 0
    val orders = mutableSetOf<List<String>>()
    checkShuffledStartPlans(services, rounds = 30) { plan ->
      rounds++
      orders += plan.map { it.name }
      assert(plan.indexOfService<Named>() == 0 && plan.first().name == "pool")
    }
    assert(rounds == 30)
    // the ties were shuffled
    assert(orders.size > 1)
  }

  test("a failed check names the seed of its plan, which replays it") {
    val failure = runCatching {
      checkShuffledStartPlans(services, random = Random(1)) { plan -> assert(plan[1].name == "s1") }
    }.exceptionOrNull()

    assert(failure is AssertionError)
    val seed = Regex("""seed=(-?\d+)""").find(failure!!.message!!)!!.groupValues[1].toLong()
    val replayed = startPlan(services, tiebreaker = StartOrder.Tiebreaker.Shuffled(seed)).map { it.name }
    assert(failure.message!!.endsWith(replayed.joinToString()))
  }
}
