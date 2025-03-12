package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.AdvancedAppService
import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.config.common.host
import com.github.rocketraman.bootable.config.common.port
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.LoggerFactory

/**
 * A base ktor app service that deals with startup and shutdown. Implements [AdvancedAppService], so `die()` can
 * be called by implementations when a fatal service occurs to force the entire system to shut down.
 */
abstract class KtorService(
  private val name: String,
  private val hostPort: HostPort
) : AdvancedAppService {

  lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

  lateinit var die: () -> Unit

  override fun start(die: () -> Unit) {
    this.die = die
    val environment = applicationEnvironment {
      log = LoggerFactory.getLogger(name)
    }

    server = embeddedServer(Netty,
      environment = environment,
      configure = {
        connector {
          host = this@KtorService.hostPort.host()
          port = this@KtorService.hostPort.port(8080)
        }
      },
      { module() }
    )
    server.start(false)
  }

  override fun shutdown() {
    if(::server.isInitialized) {
      server.stop(50, 1_000)
    }
  }

  override fun priority() = Int.MIN_VALUE

  abstract fun Application.module()
}
