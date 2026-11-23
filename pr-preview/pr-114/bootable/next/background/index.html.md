# Background work

> Loops that poll, persistent tasks that run once across the instances, and a leader to run a loop on one instance.

Bootable has three tools for work the application does in the background, rather than in answer to a request. Each is an app service, so it starts and stops with the application, in its lifecycle.

- [Periodic work](https://rocketraman.github.io/bootable/bootable/next/background/periodic.html.md), from `boot-periodic`: a loop that runs every few seconds or minutes, in the process, such as one that sends the notifications that are pending.
- [Scheduled tasks](https://rocketraman.github.io/bootable/bootable/next/background/scheduler.html.md), from `boot-scheduler-db`: db-scheduler’s tasks, kept in the application’s database, which survive restarts and run once across all the application’s instances, such as a reminder an hour from now, or a nightly report.
- [Leader election](https://rocketraman.github.io/bootable/bootable/next/background/leader.html.md), from `boot-leader-k8s`: one leader among the application’s pods, so a loop runs on one instance only.

The loops and the scheduler are in the `Serving` phase, so they start after the services their work uses, and stop before them. The leader election is in the `Infrastructure` phase, so the loops know whether they lead when they start.

## Which to use

- For work that polls, such as sending what is pending or sweeping what has expired, use a periodic loop. Every instance runs it, unless its gate says otherwise.
- For that work on one instance only, gate the loop on the leader, and add leader election.
- For work that must happen once, at a time or on a schedule, and survive a restart, use a scheduled task. [`boot-scheduler-db`](https://rocketraman.github.io/bootable/bootable/next/background/scheduler.html.md) runs them with [db-scheduler](https://github.com/kagkarlsson/db-scheduler), which runs each execution on whichever instance locks its row first, so a task needs no leader.

## Modules

Each tool is optional, and its own dependency. `boot-leader-common`, the `LeaderElection` API, comes with `boot-periodic` and `boot-leader-k8s`.

Every module but `boot-leader-common` comes in `-kodein`, `-koin`, and `-metro` flavours too, for those wiring styles.

| Module | What it provides |
| --- | --- |
| **Periodic work: optional** |  |
| [`boot-periodic`](https://rocketraman.github.io/bootable/bootable/next/background/periodic.html.md) | `PeriodicAppService`: a loop in the background, on the leader only if you like<br>With telemetry: a trace for each run |
| **Leader election: optional** |  |
| [`boot-leader-k8s`](https://rocketraman.github.io/bootable/bootable/next/background/leader.html.md) | `KubernetesLeaderElection`: a leader among the pods, with a Lease |
| **Scheduled tasks: optional** |  |
| [`boot-scheduler-db`](https://rocketraman.github.io/bootable/bootable/next/background/scheduler.html.md) | `DbSchedulerService`: db-scheduler’s persistent tasks, over the application’s database<br>With telemetry: a trace for each execution |
| **Leader election API: comes with `boot-periodic` and `boot-leader-k8s`** |  |
| [`boot-leader-common`](https://rocketraman.github.io/bootable/bootable/next/background/leader.html.md) | `LeaderElection`, and `AlwaysLeader`, the leader of an application that runs as one instance |

## Install

Add the tools you use, in the flavour of your wiring style. Leader election calls the Kubernetes API server with one of the application’s HTTP clients, so it needs a client engine, here CIO; the scheduler needs a [database](https://rocketraman.github.io/bootable/bootable/next/database/index.html.md), here SQLite through HikariCP, with Flyway to create its table.

**Vanilla:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-periodic:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-leader-k8s:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-scheduler-db:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-periodic:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-leader-k8s:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-otel:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-scheduler-db:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-otel:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-otel:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel:3.1.0-SNAPSHOT")
}
```

**Kodein:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-periodic-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-leader-k8s-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-scheduler-db-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-kodein:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-periodic-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-leader-k8s-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-otel-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-scheduler-db-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-otel-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-otel-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-periodic-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-leader-k8s-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-scheduler-db-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-koin:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-periodic-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-leader-k8s-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-otel-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-scheduler-db-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-otel-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-otel-koin:3.1.0-SNAPSHOT")
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
  implementation("com.github.rocketraman.bootable:boot-periodic-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-leader-k8s-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-scheduler-db-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-metro:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-periodic-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-leader-k8s-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-otel-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-scheduler-db-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-otel-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-otel-metro:3.1.0-SNAPSHOT")
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
  implementation("com.github.rocketraman.bootable:boot-periodic-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-leader-k8s-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-scheduler-db-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.3.0")
}
```

With telemetry:

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.13"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-periodic-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-leader-k8s-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-otel-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-scheduler-db-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-otel-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-otel-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.3.0")
}
```

### With telemetry

Turn on the **Telemetry** switch, at the end of the tabs above, for the install with telemetry: it adds `boot-telemetry-otel`, the application’s OpenTelemetry SDK. With Kodein, Koin, Metro, or Inara, the loops and the scheduler then trace each run and each execution on their own, through its `OpenTelemetry`; with vanilla wiring, you pass them the tracing. It turns on the other modules' telemetry too: the pool’s metrics, a span for each statement, and the HTTP client’s calls to the Kubernetes API server.

[Periodic work](https://rocketraman.github.io/bootable/bootable/next/background/periodic.html.md#telemetry) and [Scheduled tasks](https://rocketraman.github.io/bootable/bootable/next/background/scheduler.html.md#telemetry) show how to wire the tracing, and how to change or turn it off.

## Example

`examples/server-ktor-db` has a loop that logs the number of entries, on the leader only, and a recurring db-scheduler task that logs a digest of them, each traced. Its config skips the election, with `leader.kubernetes.assumeLeader`, so it leads when run locally. It is wired each of the five ways; from the repository root, run yours:

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

Every minute, it logs `Entry count:` from the loop, and `Entry digest:` from the task.

## Reference

- [`PeriodicAppService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-periodic/com.github.rocketraman.bootable.periodic/-periodic-app-service/index.html): a loop
- [`LeaderElection`](https://rocketraman.github.io/bootable/bootable/next/api/boot-leader-common/com.github.rocketraman.bootable.leader/-leader-election/index.html): whether this instance leads
- [`KubernetesLeaderElection`](https://rocketraman.github.io/bootable/bootable/next/api/boot-leader-k8s/com.github.rocketraman.bootable.leader.k8s/-kubernetes-leader-election/index.html): a leader among the pods
- [`DbSchedulerService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-scheduler-db/com.github.rocketraman.bootable.scheduler.db/-db-scheduler-service/index.html): db-scheduler’s tasks
