package com.github.rocketraman.bootable.boot

import java.util.PriorityQueue
import kotlin.random.Random

/**
 * The order [services] start in, as [Bootable] computes it: phase by phase, in the order of [StartOrder.phases],
 * and within each phase after the providers of the milestones each service waits for. Services left unordered are
 * taken in [tiebreaker]. They stop in the reverse order.
 *
 * Bootable calls this itself; it is public so tests can check the order their services start in, e.g. with a
 * [StartOrder.Tiebreaker.Shuffled] to check that their services declare every milestone they need.
 *
 * @throws StartOrderException if the start order or the services' orderings can't be met, listing every problem.
 */
fun startPlan(
  services: Collection<AppService>,
  startOrder: StartOrder = DefaultStartOrder,
  tiebreaker: StartOrder.Tiebreaker = StartOrder.Tiebreaker.ByName,
): List<AppService> = phasedStartPlan(services, startOrder, tiebreaker).flatMap { it.services }

/** The start order, or the services' orderings, can't be met: [problems] says why. */
class StartOrderException(val problems: List<String>) : IllegalStateException(
  "The services' start order can't be met:\n" + problems.joinToString("\n") { "  $it" }
)

/** The services of one phase, in start order. */
internal class PhasePlan(val phase: Phase, val services: List<AppService>)

/** The plan, as logged: each non-empty phase with its services, e.g. `Infrastructure[JdbcDataSourceService] …`. */
internal fun List<PhasePlan>.describe(): String =
  filter { it.services.isNotEmpty() }.joinToString(" ") { plan ->
    "${plan.phase.name}[${plan.services.joinToString { it.name() }}]"
  }

private class Node(val service: AppService, val ordering: Ordering, val phase: Phase, val phaseIndex: Int) {
  val name: String get() = service.name()
}

/** [provider] must start before the dependent, because the dependent is [relation] its [milestone]. */
private class Edge(val provider: Node, val milestone: Milestone, val relation: String)

internal fun phasedStartPlan(
  services: Collection<AppService>,
  startOrder: StartOrder,
  tiebreaker: StartOrder.Tiebreaker,
): List<PhasePlan> {
  val phases = startOrder.phases
  val phaseNames = phases.joinToString(prefix = "[", postfix = "]") { it.name }
  val problems = mutableListOf<String>()

  val repeated = phases.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
  if (repeated.isNotEmpty()) {
    problems += "The start order repeats the phase(s) ${repeated.joinToString { it.name }}: $phaseNames"
  }
  if (startOrder.defaultPhase !in phases) {
    problems += "The start order's default phase ${startOrder.defaultPhase.name} is not one of its phases: $phaseNames"
  }

  val nodes = services.mapNotNull { service ->
    val ordering = startOrder.orderingOf(service)
    val phase = ordering.phase ?: startOrder.defaultPhase
    val phaseIndex = phases.indexOf(phase)
    if (phaseIndex < 0) {
      problems += "${service.name()} is in phase ${phase.name}, which is not one of the start order's phases: " +
        "$phaseNames. Pass Bootable a StartOrder that includes ${phase.name}."
      null
    } else {
      Node(service, ordering, phase, phaseIndex)
    }
  }
  if (problems.isNotEmpty()) throw StartOrderException(problems)

  val providers = nodes
    .flatMap { node -> node.ordering.provides.map { it to node } }
    .groupBy({ it.first }, { it.second })

  val incoming = nodes.associateWith { mutableListOf<Edge>() }
  for (node in nodes) {
    node.ordering.requires.filter { it !in providers }.forEach {
      problems += "${node.name} requires milestone '${it.name}', but no service provides it. Add a service that " +
        "provides it, or use 'after' if it is optional."
    }
    val waitsFor = node.ordering.after.map { it to "after" } + node.ordering.requires.map { it to "requires" }
    for ((milestone, relation) in waitsFor) {
      for (provider in providers[milestone].orEmpty()) {
        when {
          // an earlier phase has started already
          provider.phaseIndex < node.phaseIndex -> Unit
          provider.phaseIndex == node.phaseIndex -> incoming.getValue(node) += Edge(provider, milestone, relation)
          else -> problems += "${node.name} (${node.phase.name}) must start after '${milestone.name}', but its " +
            "provider ${provider.name} is in ${provider.phase.name}, which starts later"
        }
      }
    }
  }
  if (problems.isNotEmpty()) throw StartOrderException(problems)

  // ties are broken by rank: by name, or by a shuffle of the by-name order, so a seed replays the same order
  // whatever order the services came in
  val byName = nodes.sortedBy { it.name }
  val ranked = when (tiebreaker) {
    StartOrder.Tiebreaker.ByName -> byName
    is StartOrder.Tiebreaker.Shuffled -> byName.shuffled(Random(tiebreaker.seed))
  }
  val rank = ranked.withIndex().associate { (index, node) -> node to index }

  val dependents = nodes.associateWith { mutableListOf<Node>() }
  incoming.forEach { (node, edges) -> edges.forEach { dependents.getValue(it.provider) += node } }

  val plan = phases.mapIndexed { phaseIndex, phase ->
    // Kahn's algorithm, taking the lowest-ranked of the services that are ready
    val members = nodes.filter { it.phaseIndex == phaseIndex }
    val waitingOn = members.associateWith { node -> incoming.getValue(node).map { it.provider }.toMutableSet() }
    val ready = PriorityQueue(compareBy<Node> { rank.getValue(it) })
    members.filterTo(ready) { waitingOn.getValue(it).isEmpty() }

    val ordered = mutableListOf<Node>()
    while (ready.isNotEmpty()) {
      val node = ready.poll()
      ordered += node
      for (dependent in dependents.getValue(node)) {
        val waiting = waitingOn.getValue(dependent)
        if (waiting.remove(node) && waiting.isEmpty()) ready += dependent
      }
    }

    val left = members.filter { it !in ordered }
    if (left.isNotEmpty()) problems += cycle(phase, left.minBy { rank.getValue(it) }, waitingOn, incoming)
    PhasePlan(phase, ordered.map { it.service })
  }
  if (problems.isNotEmpty()) throw StartOrderException(problems)

  return plan
}

/**
 * Describes a cycle through [start], a service left over by the sort. Every leftover service still waits on
 * another leftover one, so following those back from [start] must come round to a service already seen.
 */
private fun cycle(
  phase: Phase,
  start: Node,
  waitingOn: Map<Node, Set<Node>>,
  incoming: Map<Node, List<Edge>>,
): String {
  val path = mutableListOf<Pair<Node, Edge>>()
  var current = start
  while (path.none { it.first === current }) {
    val edge = incoming.getValue(current).first { it.provider in waitingOn.getValue(current) }
    path += current to edge
    current = edge.provider
  }
  val cycle = path.dropWhile { it.first !== current }
  return "Start-order cycle in phase ${phase.name}: " +
    cycle.joinToString(" -> ") { (node, edge) -> "${node.name} (${edge.relation} '${edge.milestone.name}')" } +
    " -> ${current.name}"
}
