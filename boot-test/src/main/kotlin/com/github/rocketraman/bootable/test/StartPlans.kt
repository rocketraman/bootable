package com.github.rocketraman.bootable.test

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultStartOrder
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.startPlan
import kotlin.random.Random

/**
 * Computes the start plan of [services] [rounds] times, each with the ties shuffled by a different
 * [StartOrder.Tiebreaker.Shuffled], and runs [check] on each, to find the services that rely on an order their
 * orderings don't declare. Nothing is started: give it the services the application's wiring builds, such as a DI
 * graph's `Set<AppService>`.
 *
 * A [check] that fails is rethrown as an [AssertionError] that names the seed, so the failing order can be replayed,
 * e.g. with `startPlan(services, tiebreaker = Shuffled(seed))`.
 */
fun checkShuffledStartPlans(
  services: Collection<AppService>,
  startOrder: StartOrder = DefaultStartOrder,
  rounds: Int = 20,
  random: Random = Random,
  check: (plan: List<AppService>) -> Unit,
) {
  repeat(rounds) {
    val tiebreaker = StartOrder.Tiebreaker.Shuffled(random.nextLong())
    val plan = startPlan(services, startOrder, tiebreaker)
    @Suppress("TooGenericExceptionCaught")
    try {
      check(plan)
    } catch (e: Throwable) {
      throw AssertionError(
        "The start plan with $tiebreaker failed the check: ${plan.joinToString { it.name }}",
        e,
      )
    }
  }
}

/** Where the first service of type [T] is in this start plan, or -1 if there is none. */
inline fun <reified T : AppService> List<AppService>.indexOfService(): Int = indexOfFirst { it is T }
