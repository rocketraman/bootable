# Database

> A connection pool tied to the application's lifecycle, with migrations and an ORM on top.

`boot-db-jdbc` runs a JDBC connection pool as part of the application’s lifecycle. It validates a connection when the application starts, so an unreachable database fails the boot, and it closes the pool on shutdown. On top of the pool, [Flyway](https://rocketraman.github.io/bootable/bootable/next/database/flyway.html.md) migrates the schema and [Exposed](https://rocketraman.github.io/bootable/bootable/next/database/exposed.html.md) gives the application a `Database` to query, both optional.

The pool is in the `Infrastructure` phase, so services that use the database start after it and stop before it. Migrations run just after the pool, and reach the `DbMilestones.SchemaMigrated` milestone, so nothing uses an unmigrated schema.

## Modules

An application makes two choices, each its own dependency: one [driver](https://rocketraman.github.io/bootable/bootable/next/database/drivers.html.md), which supplies the `JdbcConfig`, and one [connection pool](https://rocketraman.github.io/bootable/bootable/next/database/hikari.html.md), which supplies the `ConnectionPoolFactory`. Migrations, the ORM, and statement tracing are optional. Each of these is a dependency you add; the core, last, comes with the driver.

A module marked `(-otel)` has an `-otel` variant: the same module, with its telemetry added, which you use in its place. Every module comes in `-kodein`, `-koin`, and `-metro` flavours too, for those wiring styles.

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
| `boot-db-jdbc-otel` | A span for each statement, with [`jdbcTracing`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-jdbc-otel/com.github.rocketraman.bootable.db.jdbc/jdbc-tracing.html) |
| **Core: comes with the driver** |  |
| `boot-db-jdbc` | [`JdbcDataSourceService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-jdbc/com.github.rocketraman.bootable.db.jdbc/-jdbc-data-source-service/index.html): the pool, tied to the application’s lifecycle, and its health check |

## Install

Add a driver (here SQLite) and a pool (HikariCP), plus Flyway and Exposed if you use them, in the flavour of your wiring style.

**Vanilla:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-exposed:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-otel:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-exposed:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-otel:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel:3.1.0-SNAPSHOT")
}
```

**Kodein:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-exposed-kodein:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-otel-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-exposed-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-otel-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-exposed-koin:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-otel-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-exposed-koin:3.1.0-SNAPSHOT")
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
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-exposed-metro:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-otel-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-exposed-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-otel-metro:3.1.0-SNAPSHOT")
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
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-exposed-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

With telemetry:

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-sqlite-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-pool-hikari-otel-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-flyway-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-exposed-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-db-jdbc-otel-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

A driver module depends on `boot-db-jdbc`, so it needn’t be listed.

### With telemetry

Turn on the **Telemetry** switch, at the end of the tabs above, for the install with telemetry:

- `boot-db-pool-hikari-otel`, in place of `boot-db-pool-hikari`, for the pool’s metrics.
- `boot-db-jdbc-otel`, added, for a span for each statement.
- `boot-telemetry-otel`, the application’s OpenTelemetry SDK, which both report through; an exporter, such as [`boot-telemetry-otel-prometheus`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#prometheus), brings it in too.

The switch applies to every install on the site, and is remembered, as the wiring style is. [Telemetry](https://rocketraman.github.io/bootable/bootable/next/database/index.html.md#telemetry) shows how to wire them.

## Configure

Each module reads its own subsection of `db`:

```hocon
db {
  jdbc {
    path = "build/notes.db"  (1)
  }
  pool {
    poolName = notes
    connectionTimeout = 5s  (2)
  }
  flyway {
    locations = ["classpath:db/migration"]  (3)
  }
  exposed {
    schemaVerification = FAIL  (4)
  }
}
```

- (1) The driver’s settings: for SQLite, the database file; see [Drivers](https://rocketraman.github.io/bootable/bootable/next/database/drivers.html.md).
- (2) HikariCP’s settings, from `HikariPoolConfig`; see [Connection pool (HikariCP)](https://rocketraman.github.io/bootable/bootable/next/database/hikari.html.md).
- (3) Where Flyway finds the migrations; see [Migrations (Flyway)](https://rocketraman.github.io/bootable/bootable/next/database/flyway.html.md).
- (4) Fail the start if the migrated schema doesn’t match the Exposed tables; see [ORM (Exposed)](https://rocketraman.github.io/bootable/bootable/next/database/exposed.html.md).

## Wire

**Vanilla:**

Construct the pool’s service, hand its `dataSource` to whatever needs connections, and pass the services to `Bootable`:

```kotlin
  val database = JdbcDataSourceService(
    binder.config<SqliteConfig>("db.jdbc"),                  // the driver's config
    HikariConnectionPoolFactory(binder.config("db.pool")),   // the pool
  )
  val exposedConfig = binder.config<ExposedConfig>("db.exposed")
  val exposed = ExposedDatabaseService(database.dataSource, exposedConfig)
  val notes = NoteRepository(exposed.database)

  Bootable(
    setOf(
      database,
      FlywayMigrationService(database.dataSource, binder.config("db.flyway")),
      exposed,
      SchemaVerificationService(exposed.database, setOf(NoteTable), exposedConfig.schemaVerification),
      // ... and the services that use `notes`
    ),
  )
```

**Kodein:**

Import the pool’s module, a driver config module, and a pool module, plus Flyway’s and Exposed’s, and register your tables in the `Set<Table>`:

```kotlin
fun appDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(jdbcDataSourceModule)   // the pool's lifecycle
  import(sqliteConfigModule)     // the driver's config, from db.jdbc
  import(hikariPoolModule)       // the pool, from db.pool
  import(flywayMigrationModule)  // migrations, from db.flyway
  import(exposedDatabaseModule)  // the Exposed Database, from db.exposed

  inBindSet<Table> { add { singleton { NoteTable } } }  // verified against the schema
  bindSingleton { new(::NoteRepository) }
  // ... and the services that use NoteRepository
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
```

**Koin:**

Include the pool’s module, a driver config module, and a pool module, plus Flyway’s and Exposed’s, and define each of your tables as a `Table`, with a qualifier of its own:

```kotlin
fun appKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    jdbcDataSourceModule(),   // the pool's lifecycle
    sqliteConfigModule(),     // the driver's config, from db.jdbc
    hikariPoolModule(),       // the pool, from db.pool
    flywayMigrationModule(),  // migrations, from db.flyway
    exposedDatabaseModule(),  // the Exposed Database, from db.exposed
    module {
      single<Table>(named<NoteTable>()) { NoteTable }  // verified against the schema
      single { NoteRepository(get()) }
      // ... and the services that use NoteRepository
    },
  )
}.koin

fun main() = boot {
  appKoin().get<Bootable>()
}
```

**Metro:**

The `-metro` modules contribute the database’s bindings on their own; contribute your tables into the `Set<Table>`. A graph without a driver or a pool fails to compile with a missing binding.

```kotlin
// The -metro driver, pool, Flyway, and Exposed modules contribute the database's bindings; the application adds its
// tables and what uses the Database.
@ContributesTo(AppScope::class)
interface NoteBindings {
  @Provides @IntoSet fun noteTable(): Table = NoteTable  // verified against the schema

  @Provides @SingleIn(AppScope::class)
  fun noteRepository(db: Database): NoteRepository = NoteRepository(db)
  // ... and the services that use NoteRepository
}

@DependencyGraph(AppScope::class, additionalScopes = [BootScope::class])
interface AppGraph : BootableGraph

fun main() = boot {
  createGraph<AppGraph>().bootable
}
```

**Inara:**

Compose `jdbcDataSourceModule(…​)`, with the driver’s config source, over a pool module, plus Flyway’s and Exposed’s, and pass your tables to `exposedDatabaseModule(…​)`:

```kotlin
@DIModule(composed = true)
interface AppModule :
  ConfigModule, BootableModule, JdbcDataSourceModule, HikariPoolModule, FlywayMigrationModule, ExposedDatabaseModule {
  val noteRepository: NoteRepository
  // ... and the services that use NoteRepository
}

fun appModule() = AppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  // the pool's lifecycle, with the driver's config, from db.jdbc
  jdbcDataSourceModule = jdbcDataSourceModule(SqliteConfigSource),
  hikariPoolModule = hikariPoolModule(),            // the pool, from db.pool
  flywayMigrationModule = flywayMigrationModule(),  // migrations, from db.flyway
  // the Exposed Database, from db.exposed; its tables, a parameter, are verified against the schema
  exposedDatabaseModule = exposedDatabaseModule(setOf(NoteTable)),
  noteRepository = Binding.single { NoteRepository(database) },
)

fun main() = boot {
  appModule().bootable
}
```

The tables are plain Exposed tables, and repositories use the `Database` through `withTransaction`; see [ORM (Exposed)](https://rocketraman.github.io/bootable/bootable/next/database/exposed.html.md).

## Telemetry

With the [`-otel` variants](https://rocketraman.github.io/bootable/bootable/next/database/index.html.md#with-telemetry) installed, wire in the statement tracing and the pool’s metrics:

**Vanilla:**

```kotlin
  JdbcDataSourceService(
    binder.config<SqliteConfig>("db.jdbc"),
    HikariConnectionPoolFactory(
      binder.config("db.pool"),
      hikariPoolMetrics(telemetry.openTelemetry),  // the pool's metrics
    ),
    jdbcTracing(telemetry.openTelemetry),          // a span for each statement
  )
```

**Kodein:**

```kotlin
  import(openTelemetryModule)      // the application's OpenTelemetry, from boot-telemetry-otel
  import(jdbcTracingModule())      // a span for each statement
  import(hikariPoolMetricsModule)  // the pool's metrics
```

**Koin:**

```kotlin
includes(openTelemetryModule())      // the application's OpenTelemetry, from boot-telemetry-otel
includes(jdbcTracingModule())      // a span for each statement
includes(hikariPoolMetricsModule())  // the pool's metrics
```

**Metro:**

The `-metro` modules contribute the tracing wrapper and the pool’s metrics on their own. To change the instrumentation’s defaults, provide a `JdbcTelemetryConfigurer`:

```kotlin
  @Provides
  fun jdbcTelemetryConfigurer(): JdbcTelemetryConfigurer =
    JdbcTelemetryConfigurer { setDataSourceInstrumenterEnabled(true) }  // a span for each connection taken, too
```

**Inara:**

```kotlin
@DIModule(composed = true)
interface TracedAppModule :
  ConfigModule, BootableModule, JdbcDataSourceModule, HikariPoolModule,
  OpenTelemetryModule, JdbcTracingModule, HikariPoolMetricsModule

fun tracedAppModule() = TracedAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  jdbcDataSourceModule = jdbcDataSourceModule(SqliteConfigSource),
  hikariPoolModule = hikariPoolModule(),
  openTelemetryModule = openTelemetryModule(),          // the application's OpenTelemetry, from boot-telemetry-otel
  jdbcTracingModule = jdbcTracingModule(),              // a span for each statement
  hikariPoolMetricsModule = hikariPoolMetricsModule(),  // the pool's metrics
)
```

Each statement is a client span, named for its operation and table, such as `SELECT note`, a child of the span current where it runs. The query text is sanitized, with its literals replaced by `?`, and parameters aren’t recorded. The pool’s metrics are on [the HikariCP page](https://rocketraman.github.io/bootable/bootable/next/database/hikari.html.md#metrics).

## Example

`examples/server-ktor-db` is a small Ktor server that keeps entries in SQLite through a HikariCP pool, migrated by Flyway and queried with Exposed, and traces and measures itself with OpenTelemetry. It is wired each of the five ways; from the repository root, run yours:

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

It serves [http://localhost:8001](http://localhost:8001), whose index lists its endpoints.

## Reference

- [`JdbcDataSourceService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-jdbc/com.github.rocketraman.bootable.db.jdbc/-jdbc-data-source-service/index.html): the pool’s service
- [`DataSourceWrapper`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-jdbc/com.github.rocketraman.bootable.db.jdbc/-data-source-wrapper/index.html): wraps the pool’s `DataSource`, as the tracing does
- [`jdbcTracing`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-jdbc-otel/com.github.rocketraman.bootable.db.jdbc/jdbc-tracing.html): the statement tracing
