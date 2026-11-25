package com.github.rocketraman.bootable.server.grpc

import com.github.rocketraman.bootable.server.grpc.grpckotlins.grpcRequestHeaders
import com.github.rocketraman.bootable.server.grpc.test.GreeterGrpcKt
import com.github.rocketraman.bootable.server.grpc.test.HelloReply
import com.github.rocketraman.bootable.server.grpc.test.HelloRequest
import com.github.rocketraman.bootable.server.grpc.test.helloReply
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

val TEST_HEADER: Metadata.Key<String> = Metadata.Key.of("x-test", Metadata.ASCII_STRING_MARSHALLER)

fun reply(message: String): HelloReply = helloReply { this.message = message }

/** A service with a method of each kind, on the generated server base, as an application writes one. */
class GreeterService : GreeterGrpcKt.GreeterCoroutineImplBase() {
  /** Receives once for each "slow" call, as it starts, so a test can stop the server with the call in flight. */
  val slowCallStarted = Channel<Unit>(Channel.UNLIMITED)

  override suspend fun sayHello(request: HelloRequest): HelloReply {
    when (request.name) {
      "invalid" -> throw StatusException(Status.INVALID_ARGUMENT.withDescription("no such name"))
      "boom" -> error("boom")
      "slow" -> {
        slowCallStarted.send(Unit)
        delay(500.milliseconds)
      }
      "header" -> return reply("header ${grpcRequestHeaders()?.get(TEST_HEADER)}")
    }
    return reply("Hello, ${request.name}")
  }

  override fun streamHellos(request: HelloRequest): Flow<HelloReply> =
    flow { repeat(request.count) { emit(reply("Hello #$it, ${request.name}")) } }

  override suspend fun countHellos(requests: Flow<HelloRequest>): HelloReply = reply("${requests.count()} hellos")

  override fun chat(requests: Flow<HelloRequest>): Flow<HelloReply> = requests.map { reply("Hi, ${it.name}") }
}
