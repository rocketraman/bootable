package com.github.rocketraman.bootable.server.http.ktor

import io.ktor.server.netty.*
import org.kodein.di.DI
import org.kodein.di.bindSingleton

/**
 * A Kodein module that provides the application's [KtorServerEngine]: Ktor's Netty engine, on which the listeners of
 * their own of `ktorHealthModule` and `ktorTelemetryModule` run, when their `hostPort` is set.
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val ktorServerNettyEngineModule by DI.Module {
  bindSingleton { KtorServerEngine(Netty) }
}
