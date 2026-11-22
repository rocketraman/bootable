package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.BootScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.ktor.server.netty.*

/**
 * Provides the graph's [KtorServerEngine]: Ktor's Netty engine, on which the listeners of their own of
 * `KtorHealthBindings` and `KtorTelemetryBindings` run, when their `hostPort` is set.
 */
@ContributesTo(BootScope::class)
interface KtorServerNettyEngineBindings {
  companion object {
    @Provides fun ktorServerEngine(): KtorServerEngine = KtorServerEngine(Netty)
  }
}
