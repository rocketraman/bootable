package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.AdvancedAppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.config.common.HostPort
import com.github.rocketraman.bootable.config.common.host
import com.github.rocketraman.bootable.config.common.port
import io.ktor.server.application.*
import io.ktor.server.engine.*
import org.slf4j.LoggerFactory

/**
 * A base ktor app service that deals with startup and shutdown. Implements [AdvancedAppService], so `die()` can
 * be called by implementations when a fatal service occurs to force the entire system to shut down.
 *
 * The engine is supplied by [engineFactory], so this class carries no engine dependency. Rather than naming the
 * engine's types here, extend the engine's own subclass: `NettyKtorService`, `CioKtorService`, or
 * `JettyKtorService`, from the `boot-server-http-ktor-<engine>` modules. Any other engine works too, by extending
 * this class with that engine's factory, e.g. `KtorService<MyEngine, MyEngine.Configuration>(name, hostPort, MyEngine)`.
 *
 * Every engine is configured the same way: one connector at [hostPort] (port 8080 if it has none), a shutdown grace
 * period of [DEFAULT_SHUTDOWN_GRACE_PERIOD] ms, and a shutdown timeout of [DEFAULT_SHUTDOWN_TIMEOUT] ms. Override
 * [configureEngine] to change those, add connectors, or set the engine's own options.
 */
abstract class KtorService<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
  private val name: String,
  private val hostPort: HostPort,
  private val engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
) : AdvancedAppService {

  lateinit var server: EmbeddedServer<TEngine, TConfiguration>

  lateinit var die: () -> Unit

  override fun start(die: () -> Unit) {
    this.die = die
    val environment = applicationEnvironment {
      log = LoggerFactory.getLogger(name)
    }

    server = embeddedServer(engineFactory,
      environment = environment,
      configure = {
        connector {
          host = this@KtorService.hostPort.host()
          port = this@KtorService.hostPort.port(DEFAULT_PORT)
        }
        shutdownGracePeriod = DEFAULT_SHUTDOWN_GRACE_PERIOD
        shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT
        configureEngine()
      },
      { module() }
    )
    server.start(false)
  }

  override fun shutdown() {
    if(::server.isInitialized) {
      // pass both explicitly: Ktor's stop() defaults its timeout to the grace period, not to shutdownTimeout
      server.stop(server.engineConfig.shutdownGracePeriod, server.engineConfig.shutdownTimeout)
    }
  }

  /** In the [DefaultPhase.Serving] phase, so the server starts once the services it uses have, and stops first. */
  override fun ordering() = Ordering(DefaultPhase.Serving)

  /**
   * Configures the engine, after the connector and shutdown defaults are set, so anything set here wins. The
   * receiver is the engine's own configuration type, so engine-specific options are available too.
   */
  open fun TConfiguration.configureEngine() {}

  abstract fun Application.module()

  companion object {
    const val DEFAULT_PORT = 8080
    const val DEFAULT_SHUTDOWN_GRACE_PERIOD = 50L
    const val DEFAULT_SHUTDOWN_TIMEOUT = 1_000L
  }
}
