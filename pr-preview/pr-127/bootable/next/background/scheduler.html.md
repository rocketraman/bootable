# Scheduled tasks (db-scheduler)

> db-scheduler's persistent tasks, recurring and one-time, over the application's database, run once across its instances.

`boot-scheduler-db` runs [db-scheduler](https://github.com/kagkarlsson/db-scheduler) over the application’s database, with `DbSchedulerService`. Its tasks, recurring and one-time, are kept in a table, so they survive restarts, and run once across all the application’s instances: each execution runs on whichever instance locks its row first. So, unlike a periodic loop, a task needs no leader election.

The service builds db-scheduler’s `Scheduler` over `boot-db-jdbc`'s `DataSource`, and starts and stops it with the application. It is in the `Serving` phase, after the `DbMilestones.SchemaMigrated` milestone, so it starts once its table is migrated and the services its tasks use have started, and stops before them. Creating it opens no connection.

## When to use it

Use a task for work that must happen once, at a time or on a schedule, such as a reminder an hour from now, or a nightly report, and that must not be lost when the application restarts. Use a [periodic loop](https://rocketraman.github.io/bootable/bootable/next/background/periodic.html.md) for work that polls, such as sending what is pending: it runs in the process, needs no table, and runs on every instance, or, with its leader gate, on one.

## Tasks

The tasks are db-scheduler’s own, from `Tasks`. A recurring task is scheduled when the scheduler starts; a one-time task runs the executions the application schedules. A `DbSchedulerTaskProvider`, usually the class whose work its tasks do, provides them all, and exposes each typed, so code can schedule instances of it:

```kotlin
class Reminders(private val notifications: Notifications) : DbSchedulerTaskProvider {
  // one-time: an execution for each reminder, whose instance id is the reminder's id
  val send: OneTimeTask<Void> = Tasks.oneTime("send-reminder").execute { instance, _ ->
    runBlocking { notifications.sendReminder(instance.id) }  // db-scheduler runs tasks on threads of its own
  }

  // recurring: scheduled when the scheduler starts, then every hour
  val purge: RecurringTask<Void> = Tasks.recurring("purge-reminders", fixedDelay(1.hours)).execute { _, _ ->
    notifications.purgeSent()
  }

  override fun tasks() = listOf(send, purge)
}
```

db-scheduler runs each execution on a thread of its own, so a task calls suspending code with `runBlocking`. `fixedDelay` and `exponentialBackoff`, a failure handler, take Kotlin’s `Duration`.

### Schedule a one-time task

The `Scheduler` is a `SchedulerClient`, which schedules, reschedules, and cancels executions. `schedule`, `scheduleIfNotExists`, `reschedule`, and `scheduledTo` take Kotlin’s `Instant`:

```kotlin
class ReminderApi(
  private val reminders: Reminders,
  private val scheduler: SchedulerClient,
  private val clock: Clock = Clock.System,
) {
  /** Sends reminder [id] in an hour, from whichever instance locks its execution first. */
  fun remindInAnHour(id: String) {
    scheduler.schedule(reminders.send.instance(id), clock.now() + 1.hours)
  }
}
```

The execution is a row in the table, so it runs even if this instance stops first.

### Task data

A task’s data, of the class given to its task, such as `Tasks.oneTime("send-reminder", Reminder::class.java)`, is stored as JSON with kotlinx-serialization, by `KotlinxSerializationTaskDataSerializer`, rather than with db-scheduler’s default Java serialization. The data’s class must be `@Serializable`, and can’t be generic: wrap a `List<String>` in a class of its own. A subclass of a sealed `@Serializable` type is stored with its discriminator, and must have a `@SerialName`, so that renaming or moving it doesn’t strand the data already stored. Unknown keys are ignored, so an older instance reads data that a newer one wrote.

For a recurring task whose schedule is chosen at runtime, `Tasks.recurringWithPersistentSchedule`, a `SerializableSchedule` (`Cron`, `FixedDelay`, or `DailyAt`) is stored with the data: implement `PersistentScheduleData` in the data’s class.

## Its table

The module ships db-scheduler’s table as a Flyway migration for PostgreSQL, MySQL 8 (and MariaDB), and SQLite. Add its location to [Flyway](https://rocketraman.github.io/bootable/bootable/next/database/flyway.html.md)'s `locations`, alongside your own, which then need naming too:

```hocon
db {
  flyway {
    locations = ["classpath:db/migration", "classpath:bootable/db-scheduler/postgresql"]
  }
}
```

The locations are `classpath:bootable/db-scheduler/postgresql`, `…/mysql`, and `…/sqlite`. Each holds a repeatable migration, `R__bootable_db_scheduler.sql`, whose statements are all idempotent (`CREATE TABLE IF NOT EXISTS`), rather than a versioned one, whose version could clash with the application’s own, or be out of order in a database that already has later ones.

It creates the table as `scheduled_tasks`, db-scheduler’s default, with its `priority` column. With another `tableName`, or for another database, create the table with a migration of your own, from db-scheduler’s DDL in its repository.

Flyway reaches `DbMilestones.SchemaMigrated`, so the scheduler starts once the table exists.

## Configure

`DbSchedulerConfig` reads `db.scheduler`. Every setting is optional, and an unset one keeps db-scheduler’s default:

```hocon
db.scheduler {
  threads = 5                     (1)
  pollingStrategy = LockAndFetch  (2)
  shutdownMaxWait = 20s           (3)
}
```

- (1) Caps how many tasks execute at once on this instance, such as to leave the pool connections for the application’s requests; 10 by default.
- (2) Fetches and locks the due executions in one statement, `SELECT … FOR UPDATE SKIP LOCKED`, for fewer statements on PostgreSQL, MySQL 8, and the other databases that support it. `Fetch`, the default, works on every database.
- (3) Lets shutdown finish within the platform’s limit. db-scheduler’s default is 30 minutes, longer than most platforms wait for a process to stop, such as Kubernetes’s 30 second grace period: set it to fit.

The others include `pollingInterval`, 10 seconds by default, `immediateExecution`, to run an execution scheduled for now at once rather than at the next poll, `heartbeatInterval`, `missedHeartbeatsLimit`, `deleteUnresolvedAfter`, `tableName`, `schedulerName`, `priority`, `dialect`, and `persistTimestampsInUtc`: see [all the settings](https://rocketraman.github.io/bootable/bootable/next/api/boot-scheduler-db/com.github.rocketraman.bootable.scheduler.db/-db-scheduler-config/index.html). The SQL dialect, `dialect`, is `Auto` by default: from the database’s JDBC URL when the service is given it, as the DI flavours do, or else detected from the database when the scheduler starts.

For a db-scheduler setting `DbSchedulerConfig` doesn’t cover, such as a `SchedulerListener`, an `ExecutionInterceptor`, or another serializer, add a `DbSchedulerCustomizer`, which adjusts db-scheduler’s `SchedulerBuilder` after the typed settings, to its set binding, or, with vanilla wiring, pass one to `DbSchedulerService`.

## Readiness

`DbSchedulerHealthCheck` is a readiness check that the scheduler is running: started, and neither paused nor shutting down. It is opt-in, since readiness that depends on the scheduler takes the instance out of rotation when it isn’t running, which helps only when the application’s requests depend on its tasks. Add it to the checks of `HealthLifecycleController`, import `dbSchedulerHealthCheckModule` with Kodein, include `dbSchedulerHealthCheckModule()` with Koin, or name `DbSchedulerHealthCheckBindings` in the graph’s `bindingContainers` with Metro.

## Wire

**Vanilla:**

Construct the service over the pool’s `DataSource`, with the tasks, and pass it to `Bootable`, with Flyway to migrate its table:

```kotlin
  val jdbcConfig = binder.config<SqliteConfig>("db.jdbc")
  val database = JdbcDataSourceService(jdbcConfig, HikariConnectionPoolFactory(binder.config("db.pool")))
  val reminders = Reminders(Notifications())
  val scheduler = DbSchedulerService(
    database.dataSource,
    binder.config("db.scheduler"),
    reminders.tasks(),
    jdbcUrl = jdbcConfig.jdbcUrl,  // the SQL dialect, from the URL
  )
  val reminderApi = ReminderApi(reminders, scheduler.scheduler)

  return Bootable(
    setOf(
      database,
      FlywayMigrationService(database.dataSource, binder.config("db.flyway")),  // migrates the scheduler's table
      scheduler,
      // ... and the services that use reminderApi
    ),
  )
```

**Kodein:**

Import `dbSchedulerModule`, which binds the `Scheduler` and the `SchedulerClient`, and add your task providers to the `Set<DbSchedulerTaskProvider>` it opens:

```kotlin
fun schedulerDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(jdbcDataSourceModule)
  import(sqliteConfigModule)
  import(hikariPoolModule)
  import(flywayMigrationModule)  // migrates the scheduler's table
  import(dbSchedulerModule)      // the scheduler, from db.scheduler, and its SchedulerClient

  bindSingleton { new(::Notifications) }
  bindSingleton { new(::Reminders) }
  inBindSet<DbSchedulerTaskProvider> { add { singleton { instance<Reminders>() } } }
  bindSingleton { ReminderApi(instance(), instance()) }  // the Reminders, and the SchedulerClient
  // ... and the services that use ReminderApi
}
```

**Koin:**

Include `dbSchedulerModule()`, which defines the `Scheduler` and the `SchedulerClient`, and runs the tasks of every `DbSchedulerTaskProvider` definition; give each of yours a qualifier of its own:

```kotlin
fun schedulerKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    jdbcDataSourceModule(),
    sqliteConfigModule(),
    hikariPoolModule(),
    flywayMigrationModule(),  // migrates the scheduler's table
    dbSchedulerModule(),      // the scheduler, from db.scheduler, and its SchedulerClient
    module {
      single { Notifications() }
      single { Reminders(get()) }
      single<DbSchedulerTaskProvider>(named<Reminders>()) { get<Reminders>() }
      single { ReminderApi(get(), get()) }  // the Reminders, and the SchedulerClient
      // ... and the services that use ReminderApi
    },
  )
}.koin
```

**Metro:**

`boot-scheduler-db-metro` contributes the service, the `Scheduler`, and the `SchedulerClient`, and `boot-db-flyway-metro` the migrations that create its table; contribute your task providers:

```kotlin
@ContributesTo(BackgroundScope::class)
interface ReminderBindings {
  @Provides @SingleIn(BackgroundScope::class)
  fun reminders(notifications: Notifications): Reminders = Reminders(notifications)

  @Provides @IntoSet
  fun reminderTasks(reminders: Reminders): DbSchedulerTaskProvider = reminders  // its tasks, to the scheduler

  @Provides @SingleIn(BackgroundScope::class)
  fun reminderApi(reminders: Reminders, scheduler: SchedulerClient): ReminderApi = ReminderApi(reminders, scheduler)
  // ... and the services that use ReminderApi
}
```

**Inara:**

Compose `dbSchedulerModule()`, which provides the `Scheduler` and the `SchedulerClient`, and mark your task providers `@DbSchedulerTaskProviders`:

```kotlin
@DIModule(composed = true)
interface SchedulerAppModule :
  ConfigModule, BootableModule, ClockModule, JdbcDataSourceModule, HikariPoolModule, FlywayMigrationModule,
  OpenTelemetryApiModule, DbSchedulerModule {
  val notifications: Notifications
  @DbSchedulerTaskProviders val reminders: Reminders  // its tasks, to the scheduler
  val reminderApi: ReminderApi
  // ... and the services that use ReminderApi
}

fun schedulerAppModule() = SchedulerAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  clockModule = clockModule(),
  jdbcDataSourceModule = jdbcDataSourceModule(SqliteConfigSource),
  hikariPoolModule = hikariPoolModule(),
  flywayMigrationModule = flywayMigrationModule(),   // migrates the scheduler's table
  openTelemetryApiModule = NoopOpenTelemetryModule,  // required: no tracing of the executions
  dbSchedulerModule = dbSchedulerModule(),           // the scheduler, from db.scheduler, and its schedulerClient
  notifications = Binding.single { Notifications() },
  reminders = Binding.single { Reminders(notifications) },
  reminderApi = Binding.single { ReminderApi(reminders, schedulerClient) },
)
```

## Telemetry

`boot-scheduler-db` can make each execution a trace of its own, with `dbSchedulerTracing`, a `DbSchedulerCustomizer`: a root span named for the task, such as `db-scheduler send-reminder`, so that the execution’s queries and log events belong to it. The span carries the task’s name, the instance’s id, and how many executions of it in a row failed before, as `bootable.db_scheduler.task`, `bootable.db_scheduler.instance`, and `bootable.db_scheduler.consecutive_failures`.

The span covers the execution’s completion too, such as the statement that deletes a one-time execution’s row or reschedules a recurring one. An execution that fails ends its span with an error status and the exception recorded, before db-scheduler’s failure handler reschedules it. db-scheduler’s own statements, such as its polls for due executions and its heartbeats, run outside any execution, so, with [statement tracing](https://rocketraman.github.io/bootable/bootable/next/database/index.html.md#telemetry), each is still a trace of its own.

It depends only on the OpenTelemetry API, through [`boot-telemetry-otel-common`](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md#spans); the application’s SDK, such as [`boot-telemetry-otel`](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md)'s, records the traces.

**Vanilla:**

Pass `dbSchedulerTracing` to `DbSchedulerService` as its customizer:

```kotlin
  DbSchedulerService(
    dataSource,
    binder.config("db.scheduler"),
    reminders.tasks(),
    customizer = dbSchedulerTracing(telemetry.openTelemetry),  // a trace for each execution
  )
```

**Kodein:**

`dbSchedulerModule` traces each execution whenever there is an `OpenTelemetry` binding, such as `openTelemetryModule`'s, and not at all when there is none:

```kotlin
  import(openTelemetryModule)  // dbSchedulerModule traces each execution
```

The tracing’s interceptor is outermost, before those that the `Set<DbSchedulerCustomizer>` adds.

**Koin:**

`dbSchedulerModule()` traces each execution whenever there is an `OpenTelemetry` definition, such as `openTelemetryModule()`'s, and not at all when there is none:

```kotlin
includes(openTelemetryModule())  // dbSchedulerModule() traces each execution
```

The tracing’s interceptor is outermost, before those of the `DbSchedulerCustomizer` definitions.

**Metro:**

`boot-scheduler-db-metro` traces each execution whenever the graph has an `OpenTelemetry`, such as `boot-telemetry-otel-metro`'s, and not at all when it has none: there is nothing to wire. The tracing’s interceptor is outermost, before those that the contributed customizers add.

**Inara:**

`dbSchedulerModule()` requires an `OpenTelemetryApiModule`, and traces each execution through it: compose `openTelemetryModule()` to trace, or `NoopOpenTelemetryModule` for none:

```kotlin
@DIModule(composed = true)
interface TracedSchedulerAppModule :
  // OpenTelemetryModule in place of OpenTelemetryApiModule: it is one, with the SDK
  ConfigModule, BootableModule, ClockModule, JdbcDataSourceModule, HikariPoolModule, OpenTelemetryModule,
  DbSchedulerModule

fun tracedSchedulerAppModule() = TracedSchedulerAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  clockModule = clockModule(),
  jdbcDataSourceModule = jdbcDataSourceModule(SqliteConfigSource),
  hikariPoolModule = hikariPoolModule(),
  openTelemetryModule = openTelemetryModule(),  // dbSchedulerModule traces each execution
  dbSchedulerModule = dbSchedulerModule(),
)
```

### Sampling, or no tracing

Each execution’s trace is sampled as the SDK’s sampler decides, such as at the configured `sampleRatio`. For tasks that run rarely enough that every execution’s trace is worth keeping, sample them all with `Sample.Always`, as [for the periodic loops](https://rocketraman.github.io/bootable/bootable/next/background/periodic.html.md#telemetry). With Kodein or Metro, a `DbSchedulerTracingOptions` binding sets that, with Koin, a `DbSchedulerTracingOptions` definition, and with Inara, `dbSchedulerModule`'s `tracingOptions`; with `enabled = false`, it turns the tracing off:

**Vanilla:**

```kotlin
  DbSchedulerService(
    dataSource,
    binder.config("db.scheduler"),
    reminders.tasks(),
    customizer = dbSchedulerTracing(telemetry.openTelemetry, Sample.Always),
  )
```

**Kodein:**

```kotlin
  bindSingleton { DbSchedulerTracingOptions(sample = Sample.Always) }  // or enabled = false, to trace no execution
```

**Koin:**

```kotlin
single { DbSchedulerTracingOptions(sample = Sample.Always) }  // or enabled = false, to trace no execution
```

**Metro:**

Provide it in a binding container that the graph names:

```kotlin
@BindingContainer
object AlwaysSampledSchedulerTracing {
  @Provides fun dbSchedulerTracingOptions(): DbSchedulerTracingOptions =
    DbSchedulerTracingOptions(sample = Sample.Always)  // or enabled = false
}

@DependencyGraph(
  BackgroundScope::class,
  additionalScopes = [BootScope::class],
  bindingContainers = [AlwaysSampledSchedulerTracing::class],
)
interface AlwaysSampledSchedulerGraph : BootableGraph
```

**Inara:**

The options are a parameter of the builder:

```kotlin
  // a parameter of the builder; or enabled = false, to trace no execution
  dbSchedulerModule = dbSchedulerModule(tracingOptions = DbSchedulerTracingOptions(sample = Sample.Always)),
```

The setting applies to every task.

## Reference

- [`DbSchedulerService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-scheduler-db/com.github.rocketraman.bootable.scheduler.db/-db-scheduler-service/index.html): the scheduler’s service
- [`DbSchedulerConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-scheduler-db/com.github.rocketraman.bootable.scheduler.db/-db-scheduler-config/index.html): its settings
- [`DbSchedulerTaskProvider`](https://rocketraman.github.io/bootable/bootable/next/api/boot-scheduler-db/com.github.rocketraman.bootable.scheduler.db/-db-scheduler-task-provider/index.html): the tasks, with DI
- [`DbSchedulerCustomizer`](https://rocketraman.github.io/bootable/bootable/next/api/boot-scheduler-db/com.github.rocketraman.bootable.scheduler.db/-db-scheduler-customizer/index.html): for the other settings
- [`DbSchedulerHealthCheck`](https://rocketraman.github.io/bootable/bootable/next/api/boot-scheduler-db/com.github.rocketraman.bootable.scheduler.db/-db-scheduler-health-check/index.html): the readiness check
- [`KotlinxSerializationTaskDataSerializer`](https://rocketraman.github.io/bootable/bootable/next/api/boot-scheduler-db/com.github.rocketraman.bootable.scheduler.db/-kotlinx-serialization-task-data-serializer/index.html): the task data’s JSON
- [`SerializableSchedule`](https://rocketraman.github.io/bootable/bootable/next/api/boot-scheduler-db/com.github.rocketraman.bootable.scheduler.db/-serializable-schedule/index.html): a schedule stored with the data
- [`dbSchedulerTracing`](https://rocketraman.github.io/bootable/bootable/next/api/boot-scheduler-db/com.github.rocketraman.bootable.scheduler.db/db-scheduler-tracing.html): a trace for each execution
- [`DbSchedulerTracingOptions`](https://rocketraman.github.io/bootable/bootable/next/api/boot-scheduler-db/com.github.rocketraman.bootable.scheduler.db/-db-scheduler-tracing-options/index.html): how the DI flavours trace the executions
