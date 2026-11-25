# Web / HTTP

> A Ktor server and Ktor clients that start and stop with the application, and its liveness and readiness probes.

Bootable runs Ktor on both sides of HTTP, tied to the application’s lifecycle. A [server](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md) is a `KtorService`, which starts once the services it uses have started, and stops first, so requests in flight can finish. The [clients](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md) are plain Ktor `HttpClient`s, owned by `KtorClientService`, which closes them only once the services that make calls have stopped. [`HealthLifecycleController`](https://rocketraman.github.io/bootable/bootable/next/http/health.html.md), in the core `boot` module, tracks the application’s lifecycle, and the server serves it as liveness and readiness probes.

The server is in the `Serving` phase, the last to start. The clients are in the `Infrastructure` phase, and reach the `HttpClientMilestones.ClientsReady` milestone. A server, its clients, and the probes are independent: an application can have any of them without the others.

## Modules

A server and a client each make one choice, an engine, which brings the server’s or the client’s core module with it. Each has an optional telemetry module, which traces and measures its requests.

Every module with DI bindings comes in `-kodein`, `-koin`, and `-metro` flavours too, for those wiring styles; the engine modules have none.

| Module | What it provides |
| --- | --- |
| **Server engine: choose one, for a server** |  |
| [`boot-server-http-ktor-cio`](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md) | Ktor’s CIO engine |
| [`boot-server-http-ktor-netty`](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md) | Ktor’s Netty engine |
| **Server telemetry: optional** |  |
| [`boot-server-http-ktor-otel`](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md#telemetry) | Each request traced and measured, and a metrics scrape |
| **Server: comes with the engine; with Kodein, Koin, or Metro, add it too, for its flavour** |  |
| [`boot-server-http-ktor`](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md) | `KtorService`: a Ktor server that starts and stops with the application, and `KtorHealth`, which serves the liveness and readiness probes |
| **Client engine: choose one, for a client** |  |
| [`boot-client-http-ktor-cio`](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md) | Ktor’s CIO engine |
| [`boot-client-http-ktor-java`](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md) | The JDK’s `HttpClient`, as Ktor’s Java engine |
| **Client telemetry: optional** |  |
| [`boot-client-http-ktor-otel`](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md#telemetry) | Each call traced and measured |
| **Client: comes with the engine; with Kodein, Koin, or Metro, add it too, for its flavour** |  |
| [`boot-client-http-ktor`](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md) | `KtorClientService`: the application’s Ktor `HttpClient`s, closed when it stops |

## Install

For a typical server: the core server module, for its DI flavour, and an engine, here CIO.

**Vanilla:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel:3.1.0-SNAPSHOT")
}
```

**Kodein:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-kodein:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-koin:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-koin:3.1.0-SNAPSHOT")
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
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-metro:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-metro:3.1.0-SNAPSHOT")
}
```

**Inara:**

Without telemetry:

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

With telemetry:

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

The engine module brings `boot-server-http-ktor` with it, so a vanilla application needn’t list it. With a DI, list it for its flavour, which provides `KtorHealth`, for the probes.

### With telemetry

Turn on the **Telemetry** switch, at the end of the tabs above, for the install with telemetry: it adds `boot-server-http-ktor-otel`, to trace and measure each request, and `boot-telemetry-otel`, the application’s OpenTelemetry SDK, which it reports through. To serve a metrics scrape, add an exporter, such as [`boot-telemetry-otel-prometheus`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#prometheus). For clients, see [Client (Ktor)](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md).

## The pages

**[Server (Ktor)](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md)**

`KtorService`, the engines, its address and engine options, and its telemetry: each request traced and measured, and the metrics scrape.

**[Client (Ktor)](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md)**

`KtorClientService`, the default and named clients, their timeouts, retries, headers, and TLS, the engines, and their telemetry.

**[Health](https://rocketraman.github.io/bootable/bootable/next/http/health.html.md)**

Liveness and readiness from the lifecycle, readiness checks, the drain delay at shutdown, and serving them with `KtorHealth`.

## Example

`examples/server-ktor-db` is a Ktor server that serves its probes, with a readiness check of its database, calls itself back through a named client, and traces and measures both. It is wired each of the five ways; from the repository root, run yours:

**Vanilla:**

```shell
./gradlew :examples:server-ktor-db:vanilla:run
```

**Kodein:**

```shell
./gradlew :examples:server-ktor-db:kodein:run
```

**Koin:**

```shell
./gradlew :examples:server-ktor-db:koin:run
```

**Metro:**

```shell
./gradlew :examples:server-ktor-db:metro:run
```

**Inara:**

```shell
./gradlew :examples:server-ktor-db:inara:run
```

It serves [http://localhost:8001](http://localhost:8001), whose index lists its endpoints, among them `/health/live`, `/health/ready`, and `/metrics`.

`examples/server-ktor` is the smallest server, wired by hand, on Netty: `./gradlew :examples:server-ktor:run` serves [http://localhost:8000](http://localhost:8000).

## Reference

- [`KtorService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-http-ktor/com.github.rocketraman.bootable.server.http.ktor/-ktor-service/index.html): the server
- [`KtorClientService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor/com.github.rocketraman.bootable.client.http.ktor/-ktor-client-service/index.html): the clients
- [`HealthLifecycleController`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-health-lifecycle-controller/index.html): liveness and readiness
- [`KtorHealth`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-http-ktor/com.github.rocketraman.bootable.server.http.ktor/-ktor-health/index.html): the probes, served
