# Server with a database

> A key/value store over HTTP, with a database, telemetry, an HTTP client, health probes, and background work, wired five ways.

`server-ktor-db` is a Ktor server that stores named entries in a SQLite database, and traces and measures itself with OpenTelemetry. Its `GET /relay/{name}` calls the server back through a lifecycle-managed Ktor `HttpClient`, as a call to another service would be made. In the background, a periodic loop logs the entry count, on the leader only, and a db-scheduler task logs a digest of the entries.

It is the example that puts most of Bootable’s functions together, and it is wired each of the five ways: the server, repository, table, migrations, and config are shared, in `common`, and `vanilla`, `kodein`, `koin`, `metro`, and `inara` are each a runnable application. `jib` builds the vanilla application into a container image, with its JVM AOT cache in it.

## What it uses

- [Database](https://rocketraman.github.io/bootable/bootable/next/database/index.html.md): SQLite ([`boot-db-jdbc-sqlite`](https://rocketraman.github.io/bootable/bootable/next/database/drivers.html.md)) through a HikariCP pool ([`boot-db-pool-hikari-otel`](https://rocketraman.github.io/bootable/bootable/next/database/hikari.html.md)), migrated by [Flyway](https://rocketraman.github.io/bootable/bootable/next/database/flyway.html.md), and queried with [Exposed](https://rocketraman.github.io/bootable/bootable/next/database/exposed.html.md), which verifies the `entry` table against the migrated schema; `boot-db-jdbc-otel` traces each query.
- [HTTP server](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md): `EntryServer`, a `CioKtorService`, on Ktor’s CIO engine.
- [HTTP client](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md): the named client `entries`, from `boot-client-http-ktor-cio`, traced by `boot-client-http-ktor-otel`.
- [Health](https://rocketraman.github.io/bootable/bootable/next/http/health.html.md): liveness and readiness, served by the server as JSON, with readiness down while the database doesn’t answer, and the [build info](https://rocketraman.github.io/bootable/bootable/next/http/health.html.md#build-info).
- [Telemetry](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md): the OpenTelemetry SDK, a Prometheus scrape at `/metrics`, the server’s request traces and metrics, the pool’s metrics, and a trace of the application’s start, with a span for each service’s start.
- [Periodic work](https://rocketraman.github.io/bootable/bootable/next/background/periodic.html.md): the entry count’s loop, gated on [leader election](https://rocketraman.github.io/bootable/bootable/next/background/leader.html.md) through a Kubernetes Lease, which the config skips outside Kubernetes (`leader.kubernetes.assumeLeader = true`).
- [Scheduled tasks](https://rocketraman.github.io/bootable/bootable/next/background/scheduler.html.md): the entry digest, a recurring db-scheduler task, whose table Flyway migrates with the application’s.
- [Configuration](https://rocketraman.github.io/bootable/bootable/next/configuration/index.html.md) and [Logging](https://rocketraman.github.io/bootable/bootable/next/logging/index.html.md), as in every example.

`SeedEntriesService` stores two sample entries on a first run. It starts after the `DbMilestones.SchemaMigrated` milestone, in the `Initialization` phase, so it runs on a migrated schema, and before the server takes a request.

## Wire

**Vanilla:**

`EntriesApp`, in `vanilla/src/main/kotlin/App.kt`, constructs every service and lifecycle controller in plain Kotlin, and `main` hands them to `Bootable`:

```kotlin
fun main() = boot {
  val app = EntriesApp(hopliteConfigBinder())
  Bootable(app.services, app.lifecycleControllers)
}
```

The order of the set doesn’t matter: each service declares where it starts, and Bootable logs the start plan.

**Kodein:**

`appDI()`, in `kodein/src/main/kotlin/App.kt`, imports each module’s Kodein module, and `entriesModule`, the application’s own bindings:

```kotlin
fun appDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(openTelemetryModule)
  // ... telemetry, server, health, and client modules
  import(jdbcDataSourceModule)
  import(sqliteConfigModule)
  import(hikariPoolModule)
  import(flywayMigrationModule)
  import(exposedDatabaseModule)
  // ... leader election, periodic, and db-scheduler modules
  import(entriesModule)
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
```

**Koin:**

`appKoin()`, in `koin/src/main/kotlin/App.kt`, includes each module’s Koin module, and `entriesModule`, the application’s own definitions, each with a qualifier of its own where it adds to the app services or the tables:

```kotlin
fun appKoin(): KoinApplication = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    openTelemetryModule(),
    // ... telemetry, server, health, and client modules
    jdbcDataSourceModule(),
    sqliteConfigModule(),
    hikariPoolModule(),
    flywayMigrationModule(),
    exposedDatabaseModule(),
    // ... leader election, periodic, and db-scheduler modules
    entriesModule,
  )
}

fun main() = boot {
  appKoin().koin.get<Bootable>()
}
```

**Metro:**

The `-metro` modules contribute their bindings on their own; `EntriesBindings`, in `metro/src/main/kotlin/App.kt`, contributes the application’s, and the graph adds two binding containers:

```kotlin
@DependencyGraph(
  AppScope::class,
  additionalScopes = [BootScope::class],
  bindingContainers = [ServiceStartTracingBindings::class, JdbcHealthCheckBindings::class],
)
interface AppGraph : BootableGraph

fun main() = boot {
  createGraph<AppGraph>().bootable
}
```

Leaving out the driver or the pool fails the compilation with a missing binding.

**Inara:**

`EntriesModule`, in `inara/src/main/kotlin/App.kt`, is the application’s own module, which requires the Bootable modules it uses; `AppModule` composes it with them, each once:

```kotlin
@DIModule(composed = true)
interface AppModule :
  ConfigModule, BootableModule, OpenTelemetryModule,
  // ... telemetry, server, health, and client modules
  JdbcDataSourceModule, HikariPoolModule, FlywayMigrationModule, ExposedDatabaseModule,
  // ... leader election, periodic, and db-scheduler modules
  EntriesModule

fun appModule(): AppModule = AppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),
  // ...
  jdbcDataSourceModule = jdbcDataSourceModule(SqliteConfigSource),
  hikariPoolModule = hikariPoolModule(),
  flywayMigrationModule = flywayMigrationModule(),
  exposedDatabaseModule = exposedDatabaseModule(setOf(EntryTable)),
  // ...
  entriesModule = entriesModule(),
)

fun main() = boot {
  appModule().bootable
}
```

Leaving out the driver or the pool, or any module another requires, fails the compilation.

## Run it

From the repository root:

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

Each serves [http://localhost:8001](http://localhost:8001), and keeps its database in its own directory, at `build/entries.db`. The config is in `common/src/main/resources/application.conf`.

## Endpoints

[http://localhost:8001](http://localhost:8001) is an index page, with a form to try each operation, the API, and the entries stored so far.

**`GET /entries`**

Lists every entry, one `name=value` per line.

**`GET /entries/{name}`**

Returns an entry’s value, or 404.

**`PUT /entries/{name}`**

Stores the request body as the entry’s value.

**`DELETE /entries/{name}`**

Removes an entry, or answers 404.

**`GET /relay/{name}`**

Gets an entry by calling `GET /entries/{name}` on this server through the `entries` HTTP client, configured under `http.client.named.entries`.

**`GET /metrics`**

The Prometheus scrape.

**`GET /health/live`**

Liveness: 200 until the application has stopped.

**`GET /health/ready`**

Readiness: 200 while the application is running and the database answers, 503 otherwise.

**`GET /health/info`**

The build info, from the `build` config section.

For example, store an entry, and get it back through the HTTP client:

```shell
curl -X PUT --data 'hello' http://localhost:8001/entries/greeting
curl http://localhost:8001/relay/greeting
```

The health endpoints answer in JSON, with the lifecycle state, and readiness with its one check, the database’s, by name:

```shell
$ curl -s http://localhost:8001/health/live
{"status":"UP","state":"Running"}
$ curl -s http://localhost:8001/health/ready
{"status":"UP","state":"Running","checks":{"jdbc":{"status":"UP"}}}
```

So a script can check the database:

```shell
curl -s http://localhost:8001/health/ready | jq -e '.checks.jdbc.status == "UP"'
```

To see readiness go down before the server stops, set a drain delay, as `HEALTH_READINESS_DRAINDELAY=10s`, stop the application with Ctrl-C, and ask for readiness during the delay.

Start the application with a version, such as `BUILD_VERSION=1.4.2`, and a test can check which build answered it:

```shell
curl -s http://localhost:8001/health/info | jq -e '.version == "1.4.2"'
```

## Metrics and traces

As configured, the example has no OTLP endpoint, so it pushes nothing: its metrics are only pulled, from its Prometheus scrape, which needs nothing else running. After a few requests, the scrape has the server’s request metrics, the pool’s, the JVM’s, and the example’s own counter, `entries_stored_total`:

```shell
curl -s http://localhost:8001/metrics | grep -E 'entries_stored|http_server_request_duration_seconds_count|db_client_connections_usage'
```

Traces are only pushed, so they need a collector, and an OTLP endpoint to reach it. With one set, the example pushes its metrics there too, every `metrics.interval`, and still serves the scrape. Grafana’s `otel-lgtm` image runs a collector, with Tempo, Prometheus, Loki, and Grafana, in a single container:

```shell
podman run --rm -p 3000:3000 -p 4318:4318 docker.io/grafana/otel-lgtm
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 TELEMETRY_SDK_METRICS_INTERVAL=5s ./gradlew :examples:server-ktor-db:vanilla:run
```

In Grafana, at [http://localhost:3000](http://localhost:3000), search Tempo for the service `entries`. Each request is a trace, with a span for the database operation, such as `entries.put`, and a span for each of its queries within that. `GET /relay/{name}` has a client span, with the server’s own `GET /entries/{name}` within it, since the client carries the trace in a `traceparent` header. The application’s start is a trace too, `start application`, with a span for each service’s start.

Jaeger takes OTLP as well: run `docker.io/jaegertracing/jaeger:latest` with ports 16686 and 4318, and open [http://localhost:16686](http://localhost:16686).

Log lines written within a request carry its `trace_id` and `span_id`.

## Start faster, with an AOT cache

The `vanilla` application has an `aotCache` task, which runs the installed application once, serving a few requests, to write its JVM AOT cache; run the application with it:

```shell
./gradlew :examples:server-ktor-db:vanilla:aotCache
cd examples/server-ktor-db/vanilla
JAVA_OPTS=-XX:AOTCache=build/aot/vanilla.aot build/install/vanilla/bin/vanilla
```

The `jib` project builds the vanilla application into a container image, `bootable-examples/server-ktor-db-jib`, with the cache in it. The JVM only uses a cache with the same JDK build and the same jars as the run that trained it, so the training runs in a container of the image itself, and the final image adds the cache as one more layer:

```shell
./gradlew :examples:server-ktor-db:jib:jibDockerBuild -PcontainerCli=podman
podman run --rm -p 8001:8001 bootable-examples/server-ktor-db-jib
```

Leave out `-PcontainerCli=podman` to use Docker. The repository’s `server-ktor-db` [Dagger](https://dagger.io) module builds the same image as a CI pipeline. `mise run demo` uses it to run the image with the `otel-lgtm` stack, in containers, with the OTLP endpoint set, so its traces and metrics are pushed to Grafana at [http://localhost:3000](http://localhost:3000).

See [Faster start: the JVM AOT cache](https://rocketraman.github.io/bootable/bootable/next/aot-cache.html.md) for how the cache is trained, and what it gains.

## Tests

```shell
./gradlew :examples:server-ktor-db:common:test :examples:server-ktor-db:vanilla:test :examples:server-ktor-db:kodein:test :examples:server-ktor-db:koin:test :examples:server-ktor-db:metro:test
```

`common` tests the routes against a real SQLite database, and each application tests its own wiring.

## Source

[`examples/server-ktor-db`](https://github.com/rocketraman/bootable/tree/master/examples/server-ktor-db)
