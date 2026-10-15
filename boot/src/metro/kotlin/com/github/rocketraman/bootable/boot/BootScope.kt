package com.github.rocketraman.bootable.boot

/**
 * The Metro scope for Bootable's own bindings (the runtime, the default controllers, config binders). Used for
 * both their lifetime (`@SingleIn(BootScope::class)`) and their discovery (`@ContributesTo(BootScope::class)`).
 *
 * Bootable declares no application scope — that is yours to name and pass as the `@DependencyGraph` argument.
 * Your graph hosts `BootScope` bindings by extending [BootableGraph] (which carries the `@SingleIn(BootScope)`
 * declaration), so you never mention `BootScope` yourself.
 *
 * Two ways to pull in Bootable's bindings:
 *
 * Magic — discovered, nothing named:
 * ```
 * @DependencyGraph(AppScope::class, additionalScopes = [BootScope::class])
 * interface AppGraph : BootableGraph
 * ```
 *
 * Explicit — named, nothing discovered from the classpath:
 * ```
 * @DependencyGraph(AppScope::class, bindingContainers = [BootableDefaults::class, ConfigBindings::class])
 * interface AppGraph : BootableGraph
 * ```
 */
sealed interface BootScope
