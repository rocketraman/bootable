package com.github.rocketraman.bootable.boot

import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.SingleIn

/**
 * The contract a Bootable application graph must satisfy. Your graph extends this **by name**:
 *
 * ```
 * @DependencyGraph(AppScope::class, additionalScopes = [BootScope::class])
 * interface AppGraph : BootableGraph
 *
 * fun main() = boot { createGraph<AppGraph>().bootable }
 * ```
 *
 * This is a plain interface, deliberately **not** annotated `@ContributesTo`, so [bootable] is visible here and
 * navigable from the `: BootableGraph` clause rather than appearing in your graph from nowhere.
 *
 * It **is** annotated `@SingleIn(BootScope::class)`: Bootable's own bindings are scoped to [BootScope], and a
 * graph may only host bindings whose scope it declares. Carrying that declaration here means your graph inherits
 * it by extending `BootableGraph` — so your graph declares only *your* application scope (as the
 * `@DependencyGraph` argument) and never has to mention `BootScope`. Bootable brings its own scope.
 */
@SingleIn(BootScope::class)
interface BootableGraph {
  /** The configured application runtime. Pass it to `boot { … }`. */
  val bootable: Bootable

  @Multibinds(allowEmpty = true) val appServices: Set<AppService>
  @Multibinds(allowEmpty = true) val lifecycleControllers: Set<LifecycleController>
}
