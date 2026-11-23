package com.github.rocketraman.bootable.server.http.ktor.koin

import com.github.rocketraman.bootable.server.http.ktor.KtorServerEngine
import io.ktor.server.cio.*
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * A Koin module that provides the application's [KtorServerEngine]: Ktor's CIO engine, on which the listeners of
 * their own of `ktorHealthModule()` and `ktorTelemetryModule()` run, when their `hostPort` is set.
 */
fun ktorServerCioEngineModule(): Module = module {
  single { KtorServerEngine(CIO) }
}
