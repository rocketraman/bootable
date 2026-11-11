package com.github.rocketraman.bootable.boot

/**
 * A set that several Inara modules contribute to, such as `BootableModule`'s services. Inara resolves everything
 * at compile time and has no multibindings, so modules add their members through an acceptor such as
 * [AppServiceAcceptor] instead, as providers, and the module that owns the set reads it once with [toSet].
 *
 * Contributions are closed by the first [toSet]: adding one after that throws, rather than being silently left out.
 */
internal class SetContributions<T : Any> {
  private val providers = mutableListOf<() -> T>()
  private var resolved: Set<T>? = null

  /** Adds [provider]'s instance to the set. It is called when the set is first read, not now. */
  operator fun plusAssign(provider: () -> T) {
    check(resolved == null) { "the set was already read; contribute to it before reading it" }
    providers += provider
  }

  /** The contributed instances, in the order they were added. */
  fun toSet(): Set<T> = resolved ?: providers.mapTo(LinkedHashSet()) { it() }.also { resolved = it }
}
