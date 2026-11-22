@file:JvmName("KtorServerCioEngineModuleInara")

package com.github.rocketraman.bootable.server.http.ktor

import io.ktor.server.cio.CIO
import org.kodein.di.inara.Binding

/**
 * A [KtorServerEngineModule] of Ktor's Cio engine, on which the listeners of their own of `KtorHealthModule` and
 * `KtorTelemetryModule` run, when their `hostPort` is set.
 */
fun ktorServerCioEngineModule(): KtorServerEngineModule =
  KtorServerEngineModule(ktorServerEngine = Binding.value(KtorServerEngine(CIO)))
