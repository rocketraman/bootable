@file:JvmName("KtorServerNettyEngineModuleInara")

package com.github.rocketraman.bootable.server.http.ktor

import io.ktor.server.netty.Netty
import org.kodein.di.inara.Binding

/**
 * A [KtorServerEngineModule] of Ktor's Netty engine, on which the listeners of their own of `KtorHealthModule` and
 * `KtorTelemetryModule` run, when their `hostPort` is set.
 */
fun ktorServerNettyEngineModule(): KtorServerEngineModule =
  KtorServerEngineModule(ktorServerEngine = Binding.value(KtorServerEngine(Netty)))
