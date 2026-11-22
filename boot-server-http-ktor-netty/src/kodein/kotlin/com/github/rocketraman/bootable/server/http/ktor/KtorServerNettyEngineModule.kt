package com.github.rocketraman.bootable.server.http.ktor

import io.ktor.server.netty.*
import org.kodein.di.DI
import org.kodein.di.bindSingleton

/**
 * A Kodein module that provides the application's [KtorServerEngine]: Ktor's Netty engine, on which the listeners of
 * their own of `ktorHealthModule` and `ktorTelemetryModule` run, when their `hostPort` is set.
 */
val ktorServerNettyEngineModule by DI.Module {
  bindSingleton { KtorServerEngine(Netty) }
}
