package com.github.rocketraman.bootable.server.http.ktor

import io.ktor.server.engine.*

/**
 * The engine on which modules start the listeners of their own, such as [KtorHealth]'s health probes and the metrics
 * scrape of boot-server-http-ktor-otel's `KtorTelemetry`, each when its `hostPort` is set.
 *
 * The Kodein and Metro flavors of the engine modules provide one: `ktorServerCioEngineModule` and
 * `ktorServerNettyEngineModule`, or `KtorServerCioEngineBindings` and `KtorServerNettyEngineBindings`. For any other
 * engine, such as Jetty, provide one of your own, e.g. `KtorServerEngine(Jetty)`.
 *
 * An application with no engine can still serve those routes on its own server: only a listener of their own needs
 * one.
 */
interface KtorServerEngine {
  /** Ktor's factory for the engine. */
  val factory: ApplicationEngineFactory<*, *>
}

/** The [KtorServerEngine] of Ktor's engine [factory], such as `KtorServerEngine(Jetty)`. */
fun KtorServerEngine(factory: ApplicationEngineFactory<*, *>): KtorServerEngine = object : KtorServerEngine {
  override val factory = factory
}
