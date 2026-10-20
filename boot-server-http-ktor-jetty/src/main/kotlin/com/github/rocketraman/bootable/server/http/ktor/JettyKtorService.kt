package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.config.common.HostPort
import io.ktor.server.jetty.jakarta.*

/**
 * A [KtorService] on Ktor's Jetty engine (`ktor-server-jetty-jakarta`, i.e. Jetty 12 and the Jakarta servlet API).
 */
abstract class JettyKtorService(
  name: String,
  hostPort: HostPort,
) : KtorService<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(name, hostPort, Jetty)
