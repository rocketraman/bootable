# gRPC server

> A gRPC server that starts and stops with the application, for services written as coroutines, with the standard health service, traced and measured.

`boot-server-grpc` runs a gRPC server as one of the application’s services: `GrpcServer` starts the server when the application starts, and stops it gracefully when it stops. The services it serves are written as coroutines, on the server bases that [kotpb-grpc-codegen](https://github.com/Kotpb/kotpb-grpc-codegen) generates from the application’s `.proto` files. The server is grpc-java’s, on its shaded Netty transport, so it can’t clash with an application’s own Netty, such as a Ktor server’s.

A `GrpcServer` is in the `Serving` phase, so it starts once the services it uses have started, and stops first, so the calls in flight can finish. It also serves the standard [gRPC health service](https://rocketraman.github.io/bootable/bootable/next/grpc/index.html.md#health), answered from the application’s lifecycle.

## Install

| Module | What it provides |
| --- | --- |
| **Server telemetry: optional** |  |
| `boot-server-grpc-otel` | Each call traced and measured, with the health service’s probes left out |
| **Server** |  |
| `boot-server-grpc` | `GrpcServer`: a gRPC server, over grpc-java, that starts and stops with the application, for services written as coroutines on the server bases kotpb-grpc-codegen generates, and `GrpcHealth`, the standard health service, answered from the application’s health |

**Vanilla:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-grpc:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-grpc:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-grpc-otel:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel:3.1.0-SNAPSHOT")
}
```

**Kodein:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-grpc-kodein:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-grpc-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-grpc-otel-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-grpc-koin:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-grpc-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-grpc-otel-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-koin:3.1.0-SNAPSHOT")
}
```

**Metro:**

Without telemetry:

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-grpc-metro:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-grpc-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-grpc-otel-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-metro:3.1.0-SNAPSHOT")
}
```

**Inara:**

Without telemetry:

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.13"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-grpc-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.3.0")
}
```

With telemetry:

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.13"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-grpc-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-grpc-otel-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.3.0")
}
```

The application generates its own messages, stubs and server bases, with protoc and kotpb-grpc-codegen as a protoc plugin, here with the [protobuf Gradle plugin](https://github.com/google/protobuf-gradle-plugin):

```kotlin
plugins {
  id("com.google.protobuf") version "0.10.1"
}

dependencies {
  // protoc's Kotlin DSL builders for the messages
  implementation("com.google.protobuf:protobuf-kotlin:4.36.2")
}

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:4.36.2" }
  plugins {
    // kotpb-grpc-codegen's native executable; on a platform it publishes none for, use its jar, "…:jvm@jar"
    create("grpckt") { artifact = "io.github.kotpb:kotpb-grpc-codegen:0.2.3" }
  }
  generateProtoTasks {
    all().configureEach {
      plugins { create("grpckt") }
      builtins { create("kotlin") }
    }
  }
}
```

`boot-server-grpc` brings grpc-java and grpc-kotlin’s coroutine runtime, `grpc-kotlin-stub`, which the generated code runs on.

## A service

For each service in a `.proto`, kotpb-grpc-codegen generates a `<Service>CoroutineImplBase`, whose methods are coroutines, and a `<Service>CoroutineStub`, for clients. A service is an ordinary class that extends the base, and overrides its methods:

```proto
service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
  rpc StreamHellos (HelloRequest) returns (stream HelloReply);
}
```

```kotlin
class GreeterService : GreeterGrpcKt.GreeterCoroutineImplBase() {
  override suspend fun sayHello(request: HelloRequest): HelloReply {
    if (request.name.isBlank()) throw StatusException(Status.INVALID_ARGUMENT.withDescription("name is required"))
    return helloReply { message = "Hello, ${request.name}" }
  }

  override fun streamHellos(request: HelloRequest): Flow<HelloReply> = flow {
    repeat(request.count) { emit(helloReply { message = "Hello #${it + 1}, ${request.name}" }) }
  }
}
```

A unary method is a `suspend` function, and a streaming one returns, or takes, a `Flow`. Each call runs in a coroutine of its own, which is cancelled when the client cancels the call, or its deadline passes.

A method fails its call with a `StatusException`, whose status the client gets. Any other exception is `UNKNOWN` to the client, with no details, and is logged. A method that isn’t overridden answers `UNIMPLEMENTED`, as does one that calls Kotlin’s `TODO()`.

A service reads the call’s headers with `grpcRequestHeaders()`:

```kotlin
private val requestId = Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER)

suspend fun requestId(): String? = grpcRequestHeaders()?.get(requestId)
```

`grpcRequestHeaders()` is in the `grpckotlins` package, with the server interceptors that fill gaps in grpc-kotlin’s runtime: the call’s headers, the caller’s OpenTelemetry span in the call’s coroutine, `UNIMPLEMENTED` for `TODO()`, and the log of other exceptions. Expect the package to shrink as grpc-kotlin, or a replacement such as kotlinx-rpc, fills those gaps.

## Settings

`GrpcServerConfig` reads `grpc.server`; every setting is optional:

```hocon
grpc.server {
  hostPort = ":50051"         (1)
  reflection = true           (2)
  shutdownGracePeriod = 10s   (3)
  shutdownTimeout = 2s
}
```

- (1) A host, if any, and a port, which defaults to 50051; with no host, the server listens on every interface.
- (2) Serves gRPC server reflection, so tools such as `grpcurl` can call the server without its `.proto` files; off by default.
- (3) At shutdown, the server takes no new calls, and waits this long for those in flight; then it cancels the rest, and waits `shutdownTimeout` for them to finish.

`maxInboundMessageSize` sets the largest message the server accepts; see [all the settings](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-grpc/com.github.rocketraman.bootable.server.grpc/-grpc-server-config/index.html).

For what the config doesn’t cover, such as TLS, keep-alive, or an interceptor of the application’s own, give the server a `GrpcServerCustomizer`, which adjusts grpc-java’s `ServerBuilder` before the server is built:

```kotlin
val keepAlive = GrpcServerCustomizer { builder -> builder.keepAliveTime(30, TimeUnit.SECONDS) }
```

With a DI, add it to the server’s customizers as you add a service, below.

## Health

`GrpcHealth` serves the standard `grpc.health.v1.Health` service on the same server, answered from the application’s [`HealthLifecycleController`](https://rocketraman.github.io/bootable/bootable/next/http/health.html.md), so a gRPC probe sees what the HTTP probes would:

| Service name | Status |
| --- | --- |
| `readiness`, and the empty name | The application’s readiness, with its checks: `SERVING` while it is running and every check is healthy. |
| `liveness` | The application’s liveness: `SERVING` until it has stopped. |
| Each service the server serves, such as `snippets.greeter.Greeter` | Its readiness. |
| Any other | `NOT_FOUND`; for `Watch`, `SERVICE_UNKNOWN`. |

For a Kubernetes gRPC probe, name the service:

```yaml
livenessProbe:
  grpc: { port: 50051, service: liveness }
readinessProbe:
  grpc: { port: 50051, service: readiness }
```

A `Watch` call sends the status, then each change, until shutdown begins. Then it sends the status once more, and ends, so a watcher doesn’t hold the graceful shutdown. So a readiness watch ends with `NOT_SERVING`, and a liveness watch, whose status doesn’t change at shutdown, just ends.

`GrpcHealthConfig` reads `health.grpc`, whose settings are the names of the liveness and readiness services, and `watchInterval`, how often a `Watch` checks for a change besides each change of the lifecycle’s state, 5 seconds by default.

## Wire

**Vanilla:**

Construct the server, with the services it serves and the `GrpcHealth` it answers health from, and pass it to `Bootable`, with its `HealthLifecycleController` in place of the default one:

```kotlin
val health = HealthLifecycleController(config = readinessConfig(binder))  // the lifecycle's health
return Bootable(
  setOf(
    GrpcServer(
      grpcServerConfig(binder),                       // from grpc.server
      setOf(GreeterService()),                        // the services it serves
      GrpcHealth(health, grpcHealthConfig(binder)),   // grpc.health.v1.Health, from health.grpc
    ),
  ),
  defaultLifecycleControllers().withHealthLifecycleController(health),
)
```

**Kodein:**

Import `grpcServerModule`, and add each service with `bindGrpcService`:

```kotlin
val greeterModule by DI.Module {
  import(grpcServerModule)  // the GrpcServer, from grpc.server, with GrpcHealth, from health.grpc
  bindGrpcService { singleton { GreeterService() } }
}

fun grpcDI() = DI {
  import(bootableModule)  // the lifecycle, and its HealthLifecycleController
  import(hopliteConfigModule)
  import(greeterModule)
}

fun main() = boot {
  grpcDI().direct.instance<Bootable>()
}
```

Add a customizer to the `Set<GrpcServerCustomizer>` it opens, with `inBindSet`.

**Koin:**

Include `grpcServerModule()`, and define each service as a `BindableService`, with a qualifier of its own:

```kotlin
val greeterModule = module {
  includes(grpcServerModule())  // the GrpcServer, from grpc.server, with GrpcHealth, from health.grpc
  single<BindableService>(named<GreeterService>()) { GreeterService() }
}

fun grpcKoin() = koinApplication {
  modules(
    bootableModule(),  // the lifecycle, and its HealthLifecycleController
    hopliteConfigModule(),
    greeterModule,
  )
}.koin

fun main() = boot {
  grpcKoin().get<Bootable>()
}
```

Define a customizer the same way, as a `GrpcServerCustomizer`.

**Metro:**

`boot-server-grpc-metro` contributes the server; contribute each service into the `Set<BindableService>`:

```kotlin
// boot-server-grpc-metro contributes the GrpcServer, from grpc.server, with GrpcHealth, from health.grpc, over the
// graph's Set<BindableService>; boot-server-grpc-otel-metro adds GrpcTelemetry to its customizers
@ContributesTo(GrpcScope::class)
interface GreeterBindings {
  @Provides @IntoSet fun greeter(): BindableService = GreeterService()
}
```

Contribute a customizer the same way, `@IntoSet`, as a `GrpcServerCustomizer`. The graph is Bootable’s, with the application’s scope:

```kotlin
@DependencyGraph(GrpcScope::class, additionalScopes = [BootScope::class])
interface GrpcGraph : BootableGraph

fun main() = boot {
  createGraph<GrpcGraph>().bootable
}
```

**Inara:**

Compose `grpcServerModule()`, which requires the `BootableModule`, and mark each service `@GrpcServices`:

```kotlin
@DIModule(composed = true)
interface GrpcAppModule : ConfigModule, BootableModule, GrpcServerModule {
  @GrpcServices val greeterService: GreeterService
}

fun grpcAppModule() = GrpcAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),      // the lifecycle, and its HealthLifecycleController
  grpcServerModule = grpcServerModule(),  // the GrpcServer, from grpc.server, with GrpcHealth, from health.grpc
  greeterService = Binding.single { GreeterService() },
)

fun main() = boot {
  grpcAppModule().bootable
}
```

Mark a customizer `@GrpcServerCustomizers`.

## Telemetry

`boot-server-grpc-otel` adds `GrpcTelemetry`, a `GrpcServerCustomizer` that installs OpenTelemetry’s gRPC server instrumentation. It traces every call, continuing the caller’s trace when the call carries one, and records the standard RPC server metrics, such as `rpc.server.duration`. It reports through the application’s OpenTelemetry SDK, from [`boot-telemetry-otel`](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md), which it brings in.

**Vanilla:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-grpc:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-grpc-otel:3.1.0-SNAPSHOT")
}
```

**Kodein:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-grpc-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-grpc-otel-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-grpc-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-grpc-otel-koin:3.1.0-SNAPSHOT")
}
```

**Metro:**

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-grpc-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-grpc-otel-metro:3.1.0-SNAPSHOT")
}
```

**Inara:**

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.13"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-grpc-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-grpc-otel-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.3.0")
}
```

The call’s span is current in its coroutine, so a span started in a method, such as with `Tracer.withSpan`, is its child, and log events carry its trace id.

It leaves the health service’s calls out of the traces, so the platform’s probes don’t fill them. `GrpcTelemetryConfig` reads `telemetry.grpc`; every setting is optional:

```hocon
telemetry.grpc {
  capturedRequestMetadata = [x-request-id]  (1)
  traceHealth = false                       (2)
}
```

- (1) Records these request headers on each call’s span, as `rpc.grpc.request.metadata.<key>`.
- (2) Traces the health service’s calls too.

For an instrumentation setting the config doesn’t cover, add a `GrpcServerTelemetryCustomizer` (Kodein `inBindSet`, Koin a definition with a qualifier of its own, Metro `@IntoSet`, Inara `@GrpcServerTelemetryCustomizers`), or, with vanilla wiring, pass one to `GrpcTelemetry`.

**Vanilla:**

Hand the `openTelemetry` of `OpenTelemetryService` to `GrpcTelemetry`, and give it to the server as a customizer:

```kotlin
val telemetry = OpenTelemetryService(telemetryConfig(binder))  // the application's OpenTelemetry
val grpcTelemetry = GrpcTelemetry(telemetry.openTelemetry, binder.config("telemetry.grpc"))
return Bootable(
  setOf(
    telemetry,
    GrpcServer(
      grpcServerConfig(binder),
      setOf(GreeterService()),
      GrpcHealth(health, grpcHealthConfig(binder)),
      customizers = setOf(grpcTelemetry),
    ),
  ),
  defaultLifecycleControllers().withHealthLifecycleController(health),
)
```

**Kodein:**

Import `grpcTelemetryModule`, with `openTelemetryModule`:

```kotlin
import(bootableModule)
import(hopliteConfigModule)
import(openTelemetryModule)  // the application's OpenTelemetry, from boot-telemetry-otel
import(greeterModule)
import(grpcTelemetryModule)  // GrpcTelemetry, from telemetry.grpc, one of the server's customizers
```

**Koin:**

Include `grpcTelemetryModule()`, with `openTelemetryModule()`:

```kotlin
bootableModule(),
hopliteConfigModule(),
openTelemetryModule(),  // the application's OpenTelemetry, from boot-telemetry-otel
greeterModule,
grpcTelemetryModule(),  // GrpcTelemetry, from telemetry.grpc, one of the server's customizers
```

**Metro:**

`boot-server-grpc-otel-metro` contributes `GrpcTelemetry` to the server’s customizers: add it, and `boot-telemetry-otel-metro`, as dependencies.

**Inara:**

Compose `grpcTelemetryModule()`, which requires an `OpenTelemetryApiModule`, such as `openTelemetryModule()`:

```kotlin
@DIModule(composed = true)
interface TracedGrpcAppModule :
  ConfigModule, BootableModule, OpenTelemetryModule, GrpcServerModule, GrpcTelemetryModule {
  @GrpcServices val greeterService: GreeterService
}

fun tracedGrpcAppModule() = TracedGrpcAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),  // the application's OpenTelemetry, from boot-telemetry-otel
  grpcServerModule = grpcServerModule(),
  grpcTelemetryModule = grpcTelemetryModule(),  // GrpcTelemetry, from telemetry.grpc, one of the server's customizers
  greeterService = Binding.single { GreeterService() },
)
```

## Example

`examples/server-grpc` is a gRPC server with a unary and a server-streaming method, and the health service, wired by hand; see [gRPC server](https://rocketraman.github.io/bootable/bootable/next/examples/server-grpc.html.md).

## Reference

- [`GrpcServer`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-grpc/com.github.rocketraman.bootable.server.grpc/-grpc-server/index.html): the server
- [`GrpcServerConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-grpc/com.github.rocketraman.bootable.server.grpc/-grpc-server-config/index.html): its settings
- [`GrpcServerCustomizer`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-grpc/com.github.rocketraman.bootable.server.grpc/-grpc-server-customizer/index.html): for what the settings don’t cover
- [`GrpcHealth`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-grpc/com.github.rocketraman.bootable.server.grpc/-grpc-health/index.html): the health service
- [`GrpcHealthConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-grpc/com.github.rocketraman.bootable.server.grpc/-grpc-health-config/index.html): its settings
- [`GrpcTelemetry`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-grpc-otel/com.github.rocketraman.bootable.server.grpc/-grpc-telemetry/index.html): the telemetry
- [`GrpcTelemetryConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-grpc-otel/com.github.rocketraman.bootable.server.grpc/-grpc-telemetry-config/index.html): its settings
