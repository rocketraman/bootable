package com.github.rocketraman.bootable.server.http.ktor.koin

import com.github.rocketraman.bootable.server.http.ktor.KtorServerEngine
import io.ktor.server.netty.*
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * A Koin module that provides the application's [KtorServerEngine]: Ktor's Netty engine, on which the listeners of
 * their own of `ktorHealthModule()` and `ktorTelemetryModule()` run, when their `hostPort` is set.
 */
fun ktorServerNettyEngineModule(): Module = module {
  single { KtorServerEngine(Netty) }
}
