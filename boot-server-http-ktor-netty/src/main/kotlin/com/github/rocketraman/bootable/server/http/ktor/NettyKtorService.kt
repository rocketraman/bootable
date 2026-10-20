package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.config.common.HostPort
import io.ktor.server.netty.*

/**
 * A [KtorService] on Ktor's Netty engine. This is the engine `KtorService` was hardwired to before the engine became
 * selectable.
 */
abstract class NettyKtorService(
  name: String,
  hostPort: HostPort,
) : KtorService<NettyApplicationEngine, NettyApplicationEngine.Configuration>(name, hostPort, Netty)
