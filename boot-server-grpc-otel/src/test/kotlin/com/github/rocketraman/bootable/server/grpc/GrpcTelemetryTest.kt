package com.github.rocketraman.bootable.server.grpc

import com.github.rocketraman.bootable.boot.HealthLifecycleController
import com.github.rocketraman.bootable.server.grpc.health.HealthGrpcKt
import com.github.rocketraman.bootable.server.grpc.health.healthCheckRequest
import com.github.rocketraman.bootable.server.grpc.test.GreeterGrpcKt
import com.github.rocketraman.bootable.server.grpc.test.HelloReply
import com.github.rocketraman.bootable.server.grpc.test.HelloRequest
import com.github.rocketraman.bootable.server.grpc.test.helloReply
import com.github.rocketraman.bootable.server.grpc.test.helloRequest
import de.infix.testBalloon.framework.core.testSuite
import io.grpc.ManagedChannelBuilder
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

val GrpcTelemetryTest by testSuite {
  test("traces each call, with the call's span current in its coroutine handler, and the health service untraced") {
    withContext(Dispatchers.Default) {
      val spans = InMemorySpanExporter.create()
      val otel = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build())
        .build()
      val tracer = otel.getTracer("test")
      val greeter = object : GreeterGrpcKt.GreeterCoroutineImplBase() {
        override suspend fun sayHello(request: HelloRequest): HelloReply {
          // resumed on another thread of the dispatcher, the call's span is still the current one
          yield()
          tracer.spanBuilder("inner").startSpan().end()
          return helloReply { message = "Hello, ${request.name}" }
        }
      }
      val grpc = GrpcServer(
        GrpcServerConfig(hostPort = "127.0.0.1:0"),
        setOf(greeter),
        GrpcHealth(HealthLifecycleController()),
        setOf(GrpcTelemetry(otel)),
      )
      grpc.start()
      val channel = ManagedChannelBuilder.forAddress("127.0.0.1", grpc.port).usePlaintext().build()
      try {
        GreeterGrpcKt.GreeterCoroutineStub(channel).sayHello(helloRequest { name = "otel" })
        HealthGrpcKt.HealthCoroutineStub(channel).check(healthCheckRequest {})
      } finally {
        channel.shutdownNow()
        grpc.shutdown()
      }

      val finished = spans.finishedSpanItems
      val server = finished.single { it.kind == SpanKind.SERVER }
      assert(server.name == "bootable.test.Greeter/SayHello")
      val inner = finished.single { it.name == "inner" }
      assert(inner.parentSpanId == server.spanId)
      assert(inner.traceId == server.traceId)
    }
  }
}
