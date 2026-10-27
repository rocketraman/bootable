package com.github.rocketraman.bootable.boot

/**
 * A step in the coarse start order: services start phase by phase, in the order of [StartOrder.phases], and stop
 * in the reverse order. Any type can be one: an enum's entries are phases as they are, since an enum already has a
 * [name]. Phases are compared with `equals`; [name] is used for messages and the logged start plan.
 *
 * Bootable's own phases are the [DefaultPhase]s.
 */
interface Phase {
  val name: String
}

/**
 * A named point in startup that a service reaches when it has started, e.g. "the schema is migrated". Services
 * [provide][Ordering.provides] milestones, and other services start [after][Ordering.after] them, so a module
 * orders itself against a role rather than against another module's classes.
 *
 * Milestones are compared with `equals`; [name] is used for messages and the logged start plan. An enum or an
 * `object` can implement it, or use [milestone] for one that is just a name.
 */
interface Milestone {
  val name: String
}

/** A milestone that is just a name, e.g. one read from configuration. Two with the same name are equal. */
fun milestone(name: String): Milestone = NamedMilestone(name)

private data class NamedMilestone(override val name: String) : Milestone {
  override fun toString() = name
}

/**
 * Where a service starts, relative to the others: its [phase], and, within that phase, the [Milestone]s it waits
 * for and provides.
 *
 * [after] is the everyday relation. Use [requires] only for a precondition nothing else enforces: if the service
 * receives the thing it depends on in its constructor, the wiring already guarantees it exists, so [after] is
 * enough.
 */
data class Ordering(
  /** The phase to start in, or null for the [StartOrder]'s [default phase][StartOrder.defaultPhase]. */
  val phase: Phase? = null,
  /** Milestones this service has reached once its `start()` returns. */
  val provides: Set<Milestone> = emptySet(),
  /** Start after every provider of each of these, if there are any. */
  val after: Set<Milestone> = emptySet(),
  /** Like [after], but at least one provider must be present, or the boot fails. */
  val requires: Set<Milestone> = emptySet(),
)

/** Bootable's default phases, in start order. See [DefaultStartOrder]. */
enum class DefaultPhase : Phase {
  /**
   * Connections to the systems the app needs, and the plumbing everything else relies on: database pools, clients
   * for other services, caches, telemetry. It starts first and stops last, so pools close once nothing uses them,
   * and telemetry can flush what the other phases recorded while stopping.
   */
  Infrastructure,

  /**
   * Work that brings shared state into shape before the app uses it: migrations, schema checks, seed data, cache
   * warm-up. Usually it runs once, and has nothing to do at shutdown.
   *
   * Not to be confused with [LifecycleController.appInit], which runs before any service starts.
   */
  Initialization,

  /** The application's own services: what the work needs, but not what brings the work in. The default. */
  Application,

  /**
   * Whatever brings work into the app — HTTP and gRPC servers, queue consumers, schedulers — so it starts last,
   * once everything it uses is running, and stops first, so in-flight work can finish.
   */
  Serving,
}
