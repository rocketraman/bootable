@file:JvmName("KtorServerEngineModuleInara")

package com.github.rocketraman.bootable.server.http.ktor

import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/**
 * The Inara module of the application's [KtorServerEngine], on which [KtorHealthModule] and
 * boot-server-http-ktor-otel's `KtorTelemetryModule` start the listeners of their own, when their `hostPort` is set.
 *
 * The engine modules' flavors provide one: `ktorServerCioEngineModule()` and `ktorServerNettyEngineModule()`. For any
 * other engine, such as Jetty, build one of your own:
 *
 * ```
 * KtorServerEngineModule(ktorServerEngine = Binding.value(KtorServerEngine(Jetty)))
 * ```
 *
 * An application that serves those routes only on its own server needs no engine: give it [NoKtorServerEngineModule].
 */
@DIModule
interface KtorServerEngineModule {
  val ktorServerEngine: KtorServerEngine?
}

/** A [KtorServerEngineModule] with no engine, for an application whose modules need no listener of their own. */
val NoKtorServerEngineModule: KtorServerEngineModule = KtorServerEngineModule(ktorServerEngine = Binding.value(null))
