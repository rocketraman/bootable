/*
 * This package fills gaps in grpc-kotlin's runtime, until the runtime, or a replacement for it, fills them itself.
 *
 * grpc-kotlin's runtime is the `grpc-kotlin-stub` library. Bootable uses it as it is: kotpb-grpc-codegen replaces only
 * grpc-kotlin's code generator, and the server bases it generates run on that runtime. For each call, the runtime
 * starts a coroutine, runs the service's method in it, and turns what the method throws into the call's status.
 *
 * The upstream grpc-kotlin runtime has four gaps:
 *
 * - A service can't read the call's headers. This package adds `grpcRequestHeaders()`.
 * - The caller's OpenTelemetry span isn't current in the call's coroutine. The runtime passes only the gRPC `Context`
 *   to the coroutine, so this package passes the OpenTelemetry context too.
 * - A method that calls Kotlin's `TODO()` answers `UNKNOWN`. This package makes it answer `UNIMPLEMENTED`, as a method
 *   the service doesn't override does. Kotpb/kotpb-grpc-codegen#50 makes the generated code do this instead.
 * - A method that throws any other exception answers `UNKNOWN`, and nothing logs the exception. This package logs it.
 *
 * `GrpcServer` adds this package's interceptors to every server. Of this package, an application calls only
 * `grpcRequestHeaders()`. Expect this package to shrink, or go, as grpc-kotlin, or a replacement such as kotlinx-rpc,
 * fills these gaps.
 */
package com.github.rocketraman.bootable.server.grpc.grpckotlins

import io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.grpc.kotlin.CoroutineContextServerInterceptor
import io.opentelemetry.extension.kotlin.asContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import org.apache.logging.log4j.kotlin.logger
import io.opentelemetry.context.Context as OtelContext

/**
 * Returns the headers of the current gRPC call, or null outside a call.
 *
 * grpc-kotlin's runtime gives a service no way to read them. This fills that gap; see this package.
 */
suspend fun grpcRequestHeaders(): Metadata? = currentCoroutineContext()[GrpcCallElement]?.headers

/** The call a coroutine is running, in its context: see [grpcRequestHeaders]. */
internal class GrpcCallElement(val headers: Metadata) : AbstractCoroutineContextElement(GrpcCallElement) {
  companion object Key : CoroutineContext.Key<GrpcCallElement>
}

/**
 * Adds three things to each call's coroutine context:
 *
 * - the call's headers, for [grpcRequestHeaders];
 * - the call's method name, as the coroutine's name;
 * - the OpenTelemetry context that is current when the call starts. It holds the caller's span, which an earlier
 *   interceptor, such as boot-server-grpc-otel's, starts.
 *
 * grpc-kotlin's runtime adds only the call's gRPC `Context`. This fills that gap; see this package.
 */
internal object CallContextInterceptor : CoroutineContextServerInterceptor() {
  override fun coroutineContext(call: ServerCall<*, *>, headers: Metadata): CoroutineContext =
    CoroutineName(call.methodDescriptor.fullMethodName) + GrpcCallElement(headers) +
      OtelContext.current().asContextElement()
}

/**
 * Handles a call that fails with `UNKNOWN` because its method threw an exception that isn't a gRPC status:
 *
 * - If the exception is a [NotImplementedError], which Kotlin's `TODO()` throws, the call answers `UNIMPLEMENTED`
 *   instead. A method the service doesn't override answers the same.
 * - Otherwise, it logs the exception. The client gets no details, so the log is the only record of it.
 *
 * grpc-kotlin's runtime does neither. This fills that gap; see this package.
 */
internal object ServiceFailureInterceptor : ServerInterceptor {
  private val log = logger()

  override fun <Req, Resp> interceptCall(
    call: ServerCall<Req, Resp>,
    headers: Metadata,
    next: ServerCallHandler<Req, Resp>,
  ): ServerCall.Listener<Req> = next.startCall(
    object : SimpleForwardingServerCall<Req, Resp>(call) {
      override fun close(status: Status, trailers: Metadata) {
        val cause = status.cause
        if (status.code != Status.Code.UNKNOWN || cause == null) return super.close(status, trailers)
        val method = call.methodDescriptor.fullMethodName
        if (cause is NotImplementedError) {
          super.close(Status.UNIMPLEMENTED.withDescription("Method $method is unimplemented").withCause(cause), trailers)
        } else {
          log.warn(cause) { "$method failed" }
          super.close(status, trailers)
        }
      }
    },
    headers,
  )
}
