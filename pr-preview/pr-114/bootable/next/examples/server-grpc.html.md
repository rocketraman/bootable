# gRPC server

> A small gRPC server, with a unary and a server-streaming method and the standard health service, run by Bootable.

`server-grpc` is a gRPC server with one service, `Greeter`, wired with plain Kotlin and run by Bootable. It shows the least a gRPC application needs: a `.proto`, a service written as coroutines on the server base kotpb-grpc-codegen generates from it, a `GrpcServer` to serve it, and the standard health service.

## What it uses

- `boot-server-grpc`, for `GrpcServer`, and `GrpcHealth`, the health service; see [gRPC server](https://rocketraman.github.io/bootable/bootable/next/grpc/index.html.md).
- `boot-config-hoplite`, for the `grpc` and `greeter` config sections; see [Configuration](https://rocketraman.github.io/bootable/bootable/next/configuration/index.html.md).
- `boot-logging-log4j2`, for the log; see [Logging (Log4j2)](https://rocketraman.github.io/bootable/bootable/next/logging/index.html.md).

Its build generates the messages, with protoc’s Kotlin builders, and the coroutine server base and stub, from `src/main/proto/greeter.proto`:

```proto
service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
  rpc StreamHellos (HelloRequest) returns (stream HelloReply);
}
```

The service is an ordinary class on the generated base, and the server is constructed in `main`:

```kotlin
class GreeterService(private val config: GreeterConfig) : GreeterGrpcKt.GreeterCoroutineImplBase() {
  override suspend fun sayHello(request: HelloRequest): HelloReply =
    helloReply { message = "${config.greeting}, ${request.name}" }

  override fun streamHellos(request: HelloRequest): Flow<HelloReply> = flow {
    repeat(request.count) {
      delay(100.milliseconds)
      emit(helloReply { message = "${config.greeting} #${it + 1}, ${request.name}" })
    }
  }
}

fun main() = boot {
  val binder = hopliteConfigBinder()
  val health = HealthLifecycleController(config = readinessConfig(binder))
  Bootable(
    setOf(
      GrpcServer(
        grpcServerConfig(binder),
        setOf(GreeterService(binder.config("greeter"))),
        GrpcHealth(health, grpcHealthConfig(binder)),
      ),
    ),
    defaultLifecycleControllers().withHealthLifecycleController(health),
  )
}
```

Its `src/main/resources/application.conf` sets the server’s port, `:50051`, and turns on server reflection, so a client such as `grpcurl` needs no `.proto`.

## Run it

From the repository root:

```shell
./gradlew :examples:server-grpc:run
```

It serves gRPC on `localhost:50051`, until you stop it with Ctrl-C.

## Call it

With [`grpcurl`](https://github.com/fullstorydev/grpcurl), list the services, which reflection describes:

```shell
grpcurl -plaintext localhost:50051 list
```

Call the unary method:

```shell
grpcurl -plaintext -d '{"name": "you"}' localhost:50051 bootable.example.Greeter/SayHello
```

Call the server-streaming method, which sends `count` replies, 100 ms apart:

```shell
grpcurl -plaintext -d '{"name": "you", "count": 3}' localhost:50051 bootable.example.Greeter/StreamHellos
```

## Check its health

Ask for its readiness, as a Kubernetes readiness probe would; `liveness` is its liveness:

```shell
grpcurl -plaintext -d '{"service": "readiness"}' localhost:50051 grpc.health.v1.Health/Check
```

Watch its readiness, then stop the server:

```shell
grpcurl -plaintext -d '{"service": "readiness"}' localhost:50051 grpc.health.v1.Health/Watch
```

The watch sends `SERVING`, then `NOT_SERVING` when the shutdown begins, and ends, so it doesn’t hold the server’s graceful shutdown. A watch of `liveness` just ends, with no status change, since the application is still live while it stops.

## Test

Its test boots the application on a free port, and calls both methods and the health service with the generated coroutine stubs:

```shell
./gradlew :examples:server-grpc:test
```

## Source

[`examples/server-grpc`](https://github.com/rocketraman/bootable/tree/master/examples/server-grpc)
