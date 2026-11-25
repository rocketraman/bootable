package com.github.rocketraman.bootable.server.grpc

import com.github.rocketraman.bootable.boot.AppStartStopLifecycleController
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.HealthCheck
import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.boot.HealthStatus
import com.github.rocketraman.bootable.boot.LifecycleState
import com.github.rocketraman.bootable.server.grpc.health.HealthCheckResponse.ServingStatus
import com.github.rocketraman.bootable.server.grpc.health.HealthGrpcKt
import com.github.rocketraman.bootable.server.grpc.health.healthCheckRequest
import com.github.rocketraman.bootable.server.grpc.test.GreeterGrpcKt
import com.github.rocketraman.bootable.server.grpc.test.HelloReply
import com.github.rocketraman.bootable.server.grpc.test.HelloRequest
import com.github.rocketraman.bootable.server.grpc.test.helloRequest
import com.github.rocketraman.bootable.test.captureLogs
import de.infix.testBalloon.framework.core.testSuite
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.Status
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.logging.log4j.Level

/** A running application, with a [GrpcServer] serving [GreeterService], and a channel to it. */
private class RunningApp(val bootable: Bootable, val health: HealthLifecycleController, val service: GrpcServer) {
  val channel: ManagedChannel by lazy { ManagedChannelBuilder.forAddress("127.0.0.1", service.port).usePlaintext().build() }
  val greeter: GreeterGrpcKt.GreeterCoroutineStub get() = GreeterGrpcKt.GreeterCoroutineStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS)
  val healthStub: HealthGrpcKt.HealthCoroutineStub get() = HealthGrpcKt.HealthCoroutineStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS)
}

/** Boots an application with [GreeterService] served, runs [block] once it is running, then stops it. */
private suspend fun withApp(
  checks: Set<HealthCheck> = emptySet(),
  config: GrpcServerConfig = GrpcServerConfig(hostPort = "127.0.0.1:0"),
  block: suspend RunningApp.() -> Unit,
) = withContext(Dispatchers.Default) {
  // on real time, not the test's virtual time: the server, and its clients, run on threads of their own
  val health = HealthLifecycleController(checks)
  val service = GrpcServer(config, setOf(GreeterService()), GrpcHealth(health))
  val bootable = Bootable(setOf(service), setOf(AppStartStopLifecycleController(), health))
  val run = async(Dispatchers.Default) { bootable.run(maxShutdownTime = 10.seconds) }
  withTimeout(10.seconds) { health.states.first { it == LifecycleState.Running } }
  val app = RunningApp(bootable, health, service)
  try {
    app.block()
  } finally {
    bootable.stop()
    run.await()
    app.channel.shutdownNow()
  }
}

private fun request(name: String, count: Int = 0) = helloRequest {
  this.name = name
  this.count = count
}

private fun health(service: String) = healthCheckRequest { this.service = service }

private suspend fun statusOf(block: suspend () -> Unit): Status =
  Status.fromThrowable(runCatching { block() }.exceptionOrNull() ?: error("no failure"))

val GrpcServerTest by testSuite {
  testSuite("serves services written on the generated coroutine server bases") {
    test("unary") {
      withApp { assert(greeter.sayHello(request("Bootable")).message == "Hello, Bootable") }
    }

    test("server streaming") {
      withApp {
        val replies = greeter.streamHellos(request("Bootable", count = 3)).map { it.message }.toList()
        assert(replies == listOf("Hello #0, Bootable", "Hello #1, Bootable", "Hello #2, Bootable"))
      }
    }

    test("client and bidirectional streaming") {
      withApp {
        assert(greeter.countHellos(flowOf(request("x"), request("x"), request("x"), request("x"))).message == "4 hellos")
        assert(greeter.chat(flowOf(request("a"), request("b"))).map { it.message }.toList() == listOf("Hi, a", "Hi, b"))
      }
    }

    test("with the call's headers") {
      withApp {
        val headers = Metadata().apply { put(TEST_HEADER, "42") }
        assert(greeter.sayHello(request("header"), headers).message == "header 42")
      }
    }

    test("failing a call with the status a method throws, or UNKNOWN, logged") {
      withApp {
        val invalid = statusOf { greeter.sayHello(request("invalid")) }
        assert(invalid.code == Status.Code.INVALID_ARGUMENT)
        assert(invalid.description == "no such name")
        val logs = captureLogs("com.github.rocketraman.bootable.server.grpc") {
          assert(statusOf { greeter.sayHello(request("boom")) }.code == Status.Code.UNKNOWN)
        }
        // the client gets no details of the exception, so it is logged
        assert(logs.single(Level.WARN).thrown?.message == "boom")
        // a failed call fails only itself: the server goes on serving
        assert(greeter.sayHello(request("again")).message == "Hello, again")
      }
    }
  }

  testSuite("serves the standard health service from the application's health") {
    test("the server's readiness, liveness, and its services' status") {
      withApp {
        assert(healthStub.check(health("")).status == ServingStatus.SERVING)
        assert(healthStub.check(health("readiness")).status == ServingStatus.SERVING)
        assert(healthStub.check(health("liveness")).status == ServingStatus.SERVING)
        assert(healthStub.check(health(GreeterGrpcKt.SERVICE_NAME)).status == ServingStatus.SERVING)
        assert(statusOf { healthStub.check(health("nope")) }.code == Status.Code.NOT_FOUND)
      }
    }

    test("readiness is NOT_SERVING while a check is unhealthy, and liveness is still SERVING") {
      withApp(checks = setOf(HealthCheck("db") { HealthStatus.Unhealthy("down") })) {
        assert(healthStub.check(health("")).status == ServingStatus.NOT_SERVING)
        assert(healthStub.check(health("liveness")).status == ServingStatus.SERVING)
      }
    }

    test("a watch sees NOT_SERVING when shutdown begins, and then completes") {
      lateinit var statuses: kotlinx.coroutines.Deferred<List<ServingStatus>>
      withApp {
        val first = kotlinx.coroutines.CompletableDeferred<Unit>()
        statuses = async(Dispatchers.Default) {
          healthStub.watch(health("")).map { it.status }.onEach { first.complete(Unit) }.toList()
        }
        withTimeout(5.seconds) { first.await() }
      }
      // the watch completes, with no failure, once it has sent NOT_SERVING
      assert(withTimeout(5.seconds) { statuses.await() } == listOf(ServingStatus.SERVING, ServingStatus.NOT_SERVING))
    }
  }

  testSuite("shuts down gracefully") {
    test("completing a call in flight") {
      lateinit var reply: kotlinx.coroutines.Deferred<String>
      withApp {
        reply = async { greeter.sayHello(request("slow")).message }
        // let the call reach the server before the stop
        kotlinx.coroutines.delay(100)
      }
      assert(reply.await() == "Hello, slow")
    }

    test("cancelling a call still running after the grace period") {
      lateinit var status: kotlinx.coroutines.Deferred<Status>
      val config = GrpcServerConfig(hostPort = "127.0.0.1:0", shutdownGracePeriod = 0.seconds)
      withApp(config = config) {
        status = async { statusOf { greeter.sayHello(request("slow")) } }
        kotlinx.coroutines.delay(100)
      }
      assert(status.await().code == Status.Code.UNAVAILABLE || status.await().code == Status.Code.CANCELLED)
    }
  }

  test("a method the service doesn't override answers UNIMPLEMENTED") {
    withContext(Dispatchers.Default) {
      val service = object : GreeterGrpcKt.GreeterCoroutineImplBase() {}
      val grpc = GrpcServer(GrpcServerConfig(hostPort = "127.0.0.1:0"), setOf(service))
      grpc.start()
      val channel = ManagedChannelBuilder.forAddress("127.0.0.1", grpc.port).usePlaintext().build()
      try {
        val stub = GreeterGrpcKt.GreeterCoroutineStub(channel)
        assert(statusOf { stub.streamHellos(request("x")).toList() }.code == Status.Code.UNIMPLEMENTED)
      } finally {
        channel.shutdownNow()
        grpc.shutdown()
      }
    }
  }

  test("a method that calls TODO() answers UNIMPLEMENTED, unlogged") {
    withContext(Dispatchers.Default) {
      val service = object : GreeterGrpcKt.GreeterCoroutineImplBase() {
        override suspend fun sayHello(request: HelloRequest): HelloReply = TODO()
        override fun streamHellos(request: HelloRequest): Flow<HelloReply> = TODO()
        override fun chat(requests: Flow<HelloRequest>): Flow<HelloReply> = flow { TODO("not yet") }
      }
      val grpc = GrpcServer(GrpcServerConfig(hostPort = "127.0.0.1:0"), setOf(service))
      grpc.start()
      val channel = ManagedChannelBuilder.forAddress("127.0.0.1", grpc.port).usePlaintext().build()
      try {
        val stub = GreeterGrpcKt.GreeterCoroutineStub(channel)
        val logs = captureLogs("com.github.rocketraman.bootable.server.grpc") {
          // TODO() in the method itself, and in the flow it returns, as it is collected
          val unary = statusOf { stub.sayHello(request("x")) }
          assert(unary.code == Status.Code.UNIMPLEMENTED)
          assert(unary.description == "Method bootable.test.Greeter/SayHello is unimplemented")
          assert(statusOf { stub.streamHellos(request("x")).toList() }.code == Status.Code.UNIMPLEMENTED)
          assert(statusOf { stub.chat(flowOf(request("x"))).toList() }.code == Status.Code.UNIMPLEMENTED)
        }
        assert(logs.events(Level.WARN).isEmpty())
      } finally {
        channel.shutdownNow()
        grpc.shutdown()
      }
    }
  }
}
