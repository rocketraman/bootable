package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.config.common.HostPort
import io.ktor.server.cio.*

/**
 * A [KtorService] on Ktor's CIO engine: pure Kotlin coroutines, no native transport, and the engine to use for a
 * GraalVM native image.
 */
abstract class CioKtorService(
  name: String,
  hostPort: HostPort,
) : KtorService<CIOApplicationEngine, CIOApplicationEngine.Configuration>(name, hostPort, CIO)
