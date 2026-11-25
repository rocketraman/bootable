# All modules

> Every Bootable module, by function.

Every module Bootable publishes, by function. Within a function, each group’s heading says how to pick from it: choose one, or optional, both of which are dependencies you add, or comes with another module, which you don’t add yourself. A module marked `(-otel)` has an `-otel` variant: the same module, with its telemetry added, which you use in its place. A module with `-kodein`, `-koin`, `-metro`, or `-inara` flavours is installed in the flavour of your wiring style; see [Choosing a wiring style](https://rocketraman.github.io/bootable/bootable/next/wiring.html.md).

## Core

| Module | What it provides |
| --- | --- |
| `boot` | `Bootable`: the application’s services and lifecycle controllers, run phase by phase, and `Health` |
| `boot-common` | Small helpers the other modules share |
| **Clock: optional** |  |
| [`boot-clock`](https://rocketraman.github.io/bootable/bootable/next/concepts/clock.html.md) | The application’s clock, as a `kotlin.time.Clock` and a `java.time.Clock`, bound once, for a test to replace |

## Configuration

See [Configuration](https://rocketraman.github.io/bootable/bootable/next/configuration/index.html.md).

| Module | What it provides |
| --- | --- |
| **Config library: choose one** |  |
| [`boot-config-hoplite`](https://rocketraman.github.io/bootable/bootable/next/configuration/index.html.md) | `ConfigBinder` over Hoplite, from HOCON and the environment |
| **Common: comes with the config library, and the modules that read config** |  |
| `boot-config-common` | `ConfigBinder`, and `Masked`, for secrets in config |

## Environment

See [Environment](https://rocketraman.github.io/bootable/bootable/next/environment/index.html.md).

| Module | What it provides |
| --- | --- |
| [`boot-env`](https://rocketraman.github.io/bootable/bootable/next/environment/index.html.md) | `environment`: the deployment environment, such as dev or prod, from config |

## Logging

See [Logging (Log4j2)](https://rocketraman.github.io/bootable/bootable/next/logging/index.html.md).

| Module | What it provides |
| --- | --- |
| **Log4j2: comes with `boot`; add it yourself to call its API, such as `LoggingType`** |  |
| [`boot-logging-log4j2`](https://rocketraman.github.io/bootable/bootable/next/logging/index.html.md) | Log4j2, with layouts for plain, coloured, Elastic Common Schema JSON, and Google Cloud output, and `StructuredMessage` for structured fields |
| **Core: comes with Log4j2** |  |
| `boot-logging` | `BootLoggingLevel` |

## Database

See [Database](https://rocketraman.github.io/bootable/bootable/next/database/index.html.md).

| Module | What it provides |
| --- | --- |
| **Driver: choose one** |  |
| [`boot-db-jdbc-sqlite`](https://rocketraman.github.io/bootable/bootable/next/database/drivers.html.md#sqlite) | [`SqliteConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-jdbc-sqlite/com.github.rocketraman.bootable.db.jdbc/-sqlite-config/index.html), and the SQLite driver |
| [`boot-db-jdbc-postgresql`](https://rocketraman.github.io/bootable/bootable/next/database/drivers.html.md#postgresql) | [`PostgresqlConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-jdbc-postgresql/com.github.rocketraman.bootable.db.jdbc/-postgresql-config/index.html), the PostgreSQL driver, and Flyway’s PostgreSQL support |
| [`boot-db-jdbc-mysql`](https://rocketraman.github.io/bootable/bootable/next/database/drivers.html.md#mysql) | [`MysqlConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-jdbc-mysql/com.github.rocketraman.bootable.db.jdbc/-mysql-config/index.html), Connector/J, and Flyway’s MySQL support |
| **Connection pool: choose one** |  |
| [`boot-db-pool-hikari`](https://rocketraman.github.io/bootable/bootable/next/database/hikari.html.md) (`-otel`) | [`HikariConnectionPoolFactory`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-pool-hikari/com.github.rocketraman.bootable.db.jdbc/-hikari-connection-pool-factory/index.html): HikariCP, configured by [`HikariPoolConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-pool-hikari/com.github.rocketraman.bootable.db.jdbc/-hikari-pool-config/index.html)<br>Or `boot-db-pool-hikari-otel`, with telemetry: the pool’s metrics, with [`hikariPoolMetrics`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-pool-hikari-otel/com.github.rocketraman.bootable.db.jdbc/hikari-pool-metrics.html) |
| **Migrations: optional** |  |
| [`boot-db-flyway`](https://rocketraman.github.io/bootable/bootable/next/database/flyway.html.md) | [`FlywayMigrationService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-flyway/com.github.rocketraman.bootable.db.flyway/-flyway-migration-service/index.html): Flyway’s migrations, when the application starts |
| **ORM: optional** |  |
| [`boot-db-exposed`](https://rocketraman.github.io/bootable/bootable/next/database/exposed.html.md) | [`ExposedDatabaseService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-exposed/com.github.rocketraman.bootable.db.exposed/-exposed-database-service/index.html), an Exposed `Database` over the pool, and [`SchemaVerificationService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-exposed/com.github.rocketraman.bootable.db.exposed/-schema-verification-service/index.html) |
| **Statement tracing: optional** |  |
| [`boot-db-jdbc-otel`](https://rocketraman.github.io/bootable/bootable/next/database/index.html.md#telemetry) | A span for each statement, with [`jdbcTracing`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-jdbc-otel/com.github.rocketraman.bootable.db.jdbc/jdbc-tracing.html) |
| **Core: comes with the driver** |  |
| `boot-db-jdbc` | [`JdbcDataSourceService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-jdbc/com.github.rocketraman.bootable.db.jdbc/-jdbc-data-source-service/index.html): the pool, tied to the application’s lifecycle, and its health check |

## Web / HTTP

See [Web / HTTP](https://rocketraman.github.io/bootable/bootable/next/http/index.html.md).

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

## gRPC

See [gRPC server](https://rocketraman.github.io/bootable/bootable/next/grpc/index.html.md).

| Module | What it provides |
| --- | --- |
| **Server telemetry: optional** |  |
| [`boot-server-grpc-otel`](https://rocketraman.github.io/bootable/bootable/next/grpc/index.html.md#telemetry) | Each call traced and measured, with the health service’s probes left out |
| **Server** |  |
| [`boot-server-grpc`](https://rocketraman.github.io/bootable/bootable/next/grpc/index.html.md) | `GrpcServer`: a gRPC server, over grpc-java, that starts and stops with the application, for services written as coroutines on the server bases kotpb-grpc-codegen generates, and `GrpcHealth`, the standard health service, answered from the application’s health |

## Background work

See [Background work](https://rocketraman.github.io/bootable/bootable/next/background/index.html.md).

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

## Telemetry

See [Telemetry (OpenTelemetry)](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md).

| Module | What it provides |
| --- | --- |
| **SDK: add it, for export over OTLP, or an exporter or integration, which brings it in** |  |
| [`boot-telemetry-otel`](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md) | `OpenTelemetryService`: the application’s OpenTelemetry SDK, for traces and metrics, pushed over OTLP |
| **Exporters and integrations: optional** |  |
| [`boot-telemetry-otel-prometheus`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#prometheus) | The metrics, for Prometheus to scrape |
| [`boot-telemetry-otel-signoz`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#signoz) | Traces and metrics to SigNoz, over OTLP |
| [`boot-telemetry-otel-gcp`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#gcp) | Google Cloud’s resource attributes, and export to Cloud Trace and Cloud Monitoring over OTLP |
| [`boot-telemetry-otel-micrometer-bridge`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#micrometer-bridge) | A Micrometer `MeterRegistry`, for libraries that report only to Micrometer |
| **API: comes with the SDK, and with the modules that trace** |  |
| [`boot-telemetry-otel-common`](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md#spans) | `withSpan`, `withRootSpan`, and `Sample`, over the OpenTelemetry API alone, for code that traces without depending on the SDK |

## Testing

See [Testing](https://rocketraman.github.io/bootable/bootable/next/testing/index.html.md).

| Module | What it provides |
| --- | --- |
| **Test helpers: optional, as test dependencies** |  |
| [`boot-test`](https://rocketraman.github.io/bootable/bootable/next/testing/index.html.md) | Helpers for unit tests, such as a test clock, captured logs, and the start plan, without booting the application |
| [`boot-test-otel`](https://rocketraman.github.io/bootable/bootable/next/testing/index.html.md#telemetry) | An in-memory OpenTelemetry SDK, to check a component’s spans and metrics |
