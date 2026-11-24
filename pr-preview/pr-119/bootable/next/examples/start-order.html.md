# Custom start order

> A Ktor server and a ready file, ordered with a custom lifecycle phase.

`start-order` is a Ktor server that writes a _ready file_ once it is listening, and removes it before it stops. A ready file exists only while the application takes traffic, so something outside the process, such as a Kubernetes `exec` readiness probe or a supervisor, can tell when to send requests.

It shows how an application adds a phase to Bootable’s start order. Bootable’s default phases end with `Serving`, the server’s, and the ready file must be written after it. So the application adds a phase, `Announce`, after the default ones:

```kotlin
enum class AppPhase : Phase { Announce }

object AppStartOrder : StartOrder by DefaultStartOrder {
  override val phases = DefaultStartOrder.phases + AppPhase.Announce
}
```

`ReadyFileService` declares `Ordering(AppPhase.Announce)`, and `main` passes `startOrder = AppStartOrder` to `Bootable`. Shutdown runs the plan in reverse, so the ready file is removed while the server still answers. Without `AppStartOrder`, the boot fails before any service starts, because `Announce` is not one of the default phases. See [Start order](https://rocketraman.github.io/bootable/bootable/next/concepts/start-order.html.md) for how the start order works.

## What it uses

- `boot`, for the start order: `StartOrder`, `DefaultStartOrder`, `Phase`, and `Ordering`.
- `boot-server-http-ktor-cio`, for `CioKtorService`, the server on Ktor’s CIO engine; see [Server (Ktor)](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md).
- `boot-config-hoplite`, for the `server` and `ready-file` config sections; see [Configuration](https://rocketraman.github.io/bootable/bootable/next/configuration/index.html.md).
- `boot-logging-log4j2`, for the log; see [Logging (Log4j2)](https://rocketraman.github.io/bootable/bootable/next/logging/index.html.md).

It is wired with plain Kotlin.

## Run it

From the repository root:

```shell
./gradlew :examples:start-order:run
```

Bootable logs the plan before anything starts:

```kotlin
Start plan: Serving[HelloServer] Announce[ReadyFileService]
```

While it runs, [http://localhost:8005](http://localhost:8005) answers, and the ready file, `examples/start-order/build/ready`, exists. Stop it with Ctrl-C, and the log shows the file removed before the server stops.

## Source

[`examples/start-order`](https://github.com/rocketraman/bootable/tree/master/examples/start-order)
