package com.github.rocketraman.bootable.server.grpc

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.config.common.host
import com.github.rocketraman.bootable.config.common.port
import com.github.rocketraman.bootable.server.grpc.grpckotlins.CallContextInterceptor
import com.github.rocketraman.bootable.server.grpc.grpckotlins.ServiceFailureInterceptor
import com.github.rocketraman.bootable.server.grpc.grpckotlins.grpcRequestHeaders
import io.grpc.BindableService
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.protobuf.services.ProtoReflectionServiceV1
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.apache.logging.log4j.kotlin.logger

/**
 * Adjusts the gRPC server before it is built, for what [GrpcServerConfig] doesn't cover, such as an interceptor, TLS,
 * or keep-alive settings. boot-server-grpc-otel's `GrpcTelemetry` is one.
 */
fun interface GrpcServerCustomizer {
  fun customize(builder: ServerBuilder<*>)
}

/**
 * A gRPC server, over grpc-java's Netty transport, that serves [services], and the standard health service, from
 * [health], as an [AppService].
 *
 * Each service is any of grpc-java's [BindableService]s: usually a class that extends a `*CoroutineImplBase`, the
 * server base kotpb-grpc-codegen generates for each service of a `.proto`, and overrides its methods with coroutines:
 *
 * ```
 * class GreeterService : GreeterGrpcKt.GreeterCoroutineImplBase() {
 *   override suspend fun sayHello(request: HelloRequest) = helloReply { message = "Hello, ${request.name}" }
 *   override fun streamHellos(request: HelloRequest) = flow { repeat(request.count) { emit(helloReply { … }) } }
 * }
 * ```
 *
 * A method it doesn't override answers `UNIMPLEMENTED`, as does one that throws Kotlin's `NotImplementedError`, from
 * `TODO()`. Each call runs in a coroutine of its own, by default on `Dispatchers.Default`, with the call's gRPC
 * `Context`, and the caller's OpenTelemetry span, current, and its headers available from [grpcRequestHeaders]. A call
 * the client cancels, or whose deadline passes, has its coroutine cancelled. A method fails its call with a
 * `StatusException`, whose status the client gets; any other exception is `UNKNOWN`, with no details sent to the
 * client, and is logged.
 *
 * The generated bases run on grpc-kotlin's runtime library, `grpc-kotlin-stub`. The span, the headers, `UNIMPLEMENTED`
 * for `TODO()`, and the log fill gaps in that runtime. They come from the interceptors of the `grpckotlins` package,
 * which this server adds.
 *
 * It is in the [DefaultPhase.Serving] phase, so it starts once the services it uses have, and stops first. It stops
 * gracefully: it takes no new calls, waits [GrpcServerConfig.shutdownGracePeriod] for those in flight, then cancels
 * what is left, and waits [GrpcServerConfig.shutdownTimeout] for them to finish.
 *
 * ```
 * val health = HealthLifecycleController()
 * Bootable(
 *   setOf(GrpcServer(grpcServerConfig(binder), setOf(GreeterService()), GrpcHealth(health))),
 *   defaultLifecycleControllers().withHealthLifecycleController(health),
 * )
 * ```
 *
 * This module's Koin, Metro, and Inara flavors, and its deprecated Kodein flavor, add one to the application's services,
 * over the application's [BindableService]s and [GrpcServerCustomizer]s.
 */
open class GrpcServer(
  val config: GrpcServerConfig,
  private val services: Set<BindableService>,
  private val health: GrpcHealth? = null,
  private val customizers: Set<GrpcServerCustomizer> = emptySet(),
  private val name: String = "grpc",
) : AppService {

  lateinit var server: Server
    private set

  /** The port the server listens on, once started: the one it was given, or the free one it took for port 0. */
  val port: Int get() = server.port

  override fun name() = name

  /** In the [DefaultPhase.Serving] phase, so the server starts once the services it uses have, and stops first. */
  override fun ordering() = Ordering(DefaultPhase.Serving)

  override suspend fun start() {
    val builder = NettyServerBuilder.forAddress(InetSocketAddress(config.host(), config.port(GrpcServerConfig.DEFAULT_PORT)))
    val definitions = services.map { it.bindService() }
    definitions.forEach { builder.addService(it) }
    health?.let { builder.addService(it.bindService(definitions.map { d -> d.serviceDescriptor.name }.toSet())) }
    if (config.reflection) {
      builder.addService(ProtoReflectionServiceV1.newInstance())
      // the alpha version too, which older tools, such as grpcurl before 1.9, still ask for
      @Suppress("DEPRECATION")
      builder.addService(ProtoReflectionService.newInstance())
    }
    config.maxInboundMessageSize?.let { builder.maxInboundMessageSize(it) }
    // the last interceptor added runs first, so the customizers' run before these, and the call's coroutine sees what
    // they made current, such as its span
    builder.intercept(ServiceFailureInterceptor)
    builder.intercept(CallContextInterceptor)
    customizers.forEach { it.customize(builder) }
    configureServer(builder)
    server = runInterruptible(Dispatchers.IO) { builder.build().start() }
    log.info { "gRPC server $name listening on ${server.listenSockets}" }
  }

  override suspend fun shutdown() {
    if (!::server.isInitialized) return
    runInterruptible(Dispatchers.IO) {
      server.shutdown()
      if (!server.awaitTermination(config.shutdownGracePeriod.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
        log.warn { "gRPC server $name: calls still running after ${config.shutdownGracePeriod}; cancelling them" }
        server.shutdownNow()
        if (!server.awaitTermination(config.shutdownTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
          log.warn { "gRPC server $name: not terminated within ${config.shutdownTimeout} of cancelling its calls" }
        }
      }
    }
  }

  /**
   * Configures the server, after the config, the services and the [customizers] are applied, so anything set here
   * wins. Netty's own options are available too, on [NettyServerBuilder].
   */
  open fun configureServer(builder: NettyServerBuilder) {}

  companion object {
    private val log = logger()
  }
}
