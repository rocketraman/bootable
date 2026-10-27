package com.github.rocketraman.bootable.boot

import kotlin.random.Random

/**
 * The phases services start in, and how each service's [Ordering] is read. Given to [Bootable]; most apps use
 * [DefaultStartOrder].
 *
 * An app can add phases of its own, replace the phases, or place a service it didn't write, by implementing this,
 * usually by delegating to [DefaultStartOrder]:
 *
 * ```
 * object AppStartOrder : StartOrder by DefaultStartOrder {
 *   override val phases = listOf(AppPhase.Secrets) + DefaultStartOrder.phases
 * }
 * ```
 */
interface StartOrder {
  /** Every phase, in start order. Services stop in the reverse order. */
  val phases: List<Phase>

  /** The phase of a service whose ordering names none. */
  val defaultPhase: Phase

  /** The ordering of [service]. By default the service's own; override to read it from anywhere else. */
  fun orderingOf(service: AppService): Ordering = service.ordering()

  /**
   * How the services this start order leaves tied — in the same phase, with no milestone between them — are
   * started. Either way, their order is not part of the contract: if it matters, a milestone is missing.
   *
   * Given to [Bootable] next to the start order, rather than being part of it, so the two are chosen independently:
   * an app's custom start order needn't know whether ties are shuffled.
   */
  sealed interface Tiebreaker {
    /** By name, so the start plan, and the logs, are the same on every run. */
    data object ByName : Tiebreaker

    /**
     * Shuffled, to surface missing milestones that a fixed order would hide. The seed is logged with the start
     * plan, so a failing order can be replayed with `Shuffled(seed)`.
     */
    data class Shuffled(val seed: Long = Random.nextLong()) : Tiebreaker
  }
}

/** Bootable's default start order: the [DefaultPhase]s, with [DefaultPhase.Application] the default. */
object DefaultStartOrder : StartOrder {
  override val phases: List<Phase> = DefaultPhase.entries
  override val defaultPhase: Phase = DefaultPhase.Application
}
