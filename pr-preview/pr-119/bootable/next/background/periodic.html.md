# Periodic work

> PeriodicAppService: a loop in the background, gated on the leader or the application's health, with a trace for each run.

`boot-periodic` provides `PeriodicAppService`, the loop that an application would otherwise write by hand: every few seconds or minutes, send the notifications that are pending, or sweep what has expired. The application writes only the work.

It is in the `Serving` phase by default, as something that brings work into the application, so it starts after the services its work uses, and stops before them.

## The work

A loop has a name, its config, an optional gate, and its work, a suspending function:

```kotlin
  PeriodicAppService(
    "notifications",                      // its name, in its log messages and its trace
    binder.config("notifications.loop"),  // its PeriodicConfig
    PeriodicGate.leader(leaderElection),  // on the leader only
  ) {
    notifications.sendPending()
  }
```

It runs the work on a coroutine in the service’s scope, on `Dispatchers.Default` unless its `dispatcher` says otherwise. Each run starts a fixed delay, the `interval`, after the previous run ends, so runs never overlap, and a run that takes longer than the interval delays the next rather than piling up behind it.

Work that blocks, such as JDBC calls, should switch to `Dispatchers.IO` for the calls that block, or run the loop there:

```kotlin
  PeriodicAppService("purge", binder.config("purge.loop"), dispatcher = Dispatchers.IO) {
    purge()  // JDBC calls, which block
  }
```

The service notes when each run started, and when one last succeeded and failed, in `lastStarted`, `lastSucceeded`, and `lastFailed`, by its `kotlin.time.Clock`, with `consecutiveFailures` and `isRunning`, such as for a health check that the loop isn’t stuck.

## Configure

Each loop has a config section of the application’s choosing, decoded as a `PeriodicConfig`. Every setting has a default, so the section is optional:

```hocon
notifications.loop {
  interval = 5s                (1)
  runTimeout = 1m              (2)
  maxConsecutiveFailures = 10  (3)
  onFailureLimit = Shutdown    (4)
}
```

- (1) Sets how often the loop runs: the delay between one run’s end and the next one’s start, so runs never overlap; a minute by default.
- (2) Cancels a run that hangs, such as on a call that never returns, and counts it as a failure; no limit by default.
- (3) Gives up on a loop that keeps failing, such as on a record that fails every run, rather than failing forever; no limit by default.
- (4) Stops the application, with exit code 1, once it gives up, by failing the service’s scope, so that its platform restarts it, or an operator notices. The default, `StopLoop`, stops the loop and logs an error, while the rest of the application runs on.

The others are `enabled`, `initialDelay`, before the first run, and `shutdownTimeout`, how long shutdown waits for a run in progress before cancelling it, 10 seconds: see [all the settings](https://rocketraman.github.io/bootable/bootable/next/api/boot-periodic/com.github.rocketraman.bootable.periodic/-periodic-config/index.html).

## Gates

Before each run, the loop asks its `PeriodicGate`, and skips the run when the gate is closed; it asks again an interval later.

- `PeriodicGate.Always`, the default, is always open: every instance runs the loop.
- `PeriodicGate.leader(leaderElection)` is open while this instance is the [leader](https://rocketraman.github.io/bootable/bootable/next/background/leader.html.md), so only one instance runs the loop; for a leader that pauses, a run that writes sends its [fencing token](https://rocketraman.github.io/bootable/bootable/next/background/leader.html.md#fencing) with each write.
- `PeriodicGate.running(health)` is open while the application is running: once every service has started, and until shutdown begins. A loop in the `Serving` phase starts with the last services to start, so this holds its runs back only until the others in that phase, such as the HTTP server, have started too.
- `and` combines two gates: open when both are.

```kotlin
  PeriodicAppService(
    "notifications",
    binder.config("notifications.loop"),
    PeriodicGate.leader(leaderElection) and PeriodicGate.running(health),
  ) {
    notifications.sendPending()
  }
```

A gate that closes while a run is in progress, as the leader gate does when this instance stops leading, cancels the run, which isn’t counted as a failure. Cancellation is cooperative: work that blocks, such as a JDBC call, stops only at its next suspension point, unless it blocks in `runInterruptible`.

A gate of your own is a `fun interface`: `isOpen()` must return promptly, without blocking. A gate that can close during a run says so by overriding `awaitClosed()`, which suspends until it closes.

## Failures

A run that throws is handed to the loop’s `PeriodicFailureHandler`, and the loop carries on, unless `maxConsecutiveFailures` runs in a row have failed. The default handler, `PeriodicFailureHandler.Log`, logs the failure as an error. A handler of your own runs on the loop’s coroutine, so it must return promptly; what it throws is logged.

```kotlin
  PeriodicAppService(
    "notifications",
    binder.config("notifications.loop"),
    onFailure = PeriodicFailureHandler { service, failure, consecutiveFailures ->
      // in place of the default, which logs each failure as an error
      if (consecutiveFailures < 3) log.warn(failure) { "$service failed" }
      else log.error(failure) { "$service failed $consecutiveFailures times in a row" }
    },
  ) {
    notifications.sendPending()
  }
```

A run the gate skips neither fails nor succeeds, and a run that `runTimeout` cancels fails. `maxConsecutiveFailures` guards against a poison pill, such as a record that fails every run, which would otherwise fail forever.

## Start and shutdown

The loop’s first run is `initialDelay` after it starts. On shutdown, it starts no further run, and doesn’t wait for the interval: it waits up to `shutdownTimeout` for a run in progress to finish, and then cancels it.

To run a loop in another phase, or after a milestone, pass its `ordering`.

## Wire

`PeriodicServices` holds what the application’s loops share, their default gate, clock, interceptors, and failure handler, so each loop needs only its name, config, and work: `periodic.create(name, config) { …​ }`.

**Vanilla:**

Build a `PeriodicServices`, or construct each `PeriodicAppService` yourself, and pass the loops to `Bootable` with the other services:

```kotlin
  val health = HealthLifecycleController()
  // what the application's loops share: here, their gate, so they run only once the application is running
  val periodic = PeriodicServices(gate = PeriodicGate.running(health))

  return Bootable(
    setOf(
      periodic.create("notifications", binder.config("notifications.loop")) { notifications.sendPending() },
      // ... and the services the loops use
    ),
    defaultLifecycleControllers().withHealthLifecycleController(health),
  )
```

**Kodein:**

Import `periodicModule`, which binds `PeriodicServices`, and add each loop to the app-service set:

```kotlin
fun periodicDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(periodicModule)  // PeriodicServices, gated on the LeaderElection binding, if there is one

  bindSingleton { new(::Notifications) }
  bindAppService {
    singleton {
      instance<PeriodicServices>().create("notifications", instance<ConfigBinder>().config("notifications.loop")) {
        instance<Notifications>().sendPending()
      }
    }
  }
}
```

Its gate is `PeriodicGate.leader` of the `LeaderElection` binding, if there is one, such as `kubernetesLeaderElectionModule`'s; otherwise every instance runs the loops. Its clock is the `kotlin.time.Clock` binding, if there is one, and its interceptors are those of the `Set<PeriodicRunInterceptor>` it opens.

**Koin:**

Include `periodicModule()`, which defines `PeriodicServices`, and add each loop to the app services, with a qualifier of its own:

```kotlin
fun periodicKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    periodicModule(),  // PeriodicServices, gated on the LeaderElection definition, if there is one
    module {
      single { Notifications() }
      single<AppService>(named("notifications")) {
        val notifications = get<Notifications>()
        get<PeriodicServices>().create("notifications", get<ConfigBinder>().config("notifications.loop")) {
          notifications.sendPending()
        }
      }
    },
  )
}.koin
```

Its gate is `PeriodicGate.leader` of the `LeaderElection` definition, if there is one, such as `kubernetesLeaderElectionModule()`'s; otherwise every instance runs the loops. Its clock is the `kotlin.time.Clock` definition, if there is one, and its interceptors are every `PeriodicRunInterceptor` definition.

**Metro:**

`boot-periodic-metro` contributes `PeriodicServices`; contribute each loop to the app-service set:

```kotlin
@ContributesTo(BackgroundScope::class)
interface NotificationBindings {
  @Provides @SingleIn(BackgroundScope::class)
  fun notifications(): Notifications = Notifications()

  // PeriodicServices is gated on the graph's LeaderElection, if it has one
  @Provides @IntoSet
  fun notificationsLoop(periodic: PeriodicServices, binder: ConfigBinder, notifications: Notifications): AppService =
    periodic.create("notifications", binder.config("notifications.loop")) { notifications.sendPending() }
}
```

Its gate is `PeriodicGate.leader` of the graph’s `LeaderElection`, if it has one, such as `boot-leader-k8s-metro`'s; otherwise every instance runs the loops. Its clock is the graph’s `kotlin.time.Clock`, if it has one, and its interceptors are the contributed `PeriodicRunInterceptor` set.

**Inara:**

Compose `periodicModule()`, which provides `PeriodicServices`, and mark each loop `@AppServices`:

```kotlin
@DIModule(composed = true)
interface PeriodicAppModule :
  ConfigModule, BootableModule, LeaderElectionModule, OpenTelemetryApiModule, PeriodicModule {
  val notifications: Notifications
  @AppServices val notificationsLoop: PeriodicAppService
}

fun periodicAppModule() = PeriodicAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  leaderElectionModule = AlwaysLeaderModule,         // required: every instance runs the loops
  openTelemetryApiModule = NoopOpenTelemetryModule,  // required: no tracing of the runs
  periodicModule = periodicModule(),                 // periodicServices, gated on the leaderElection
  notifications = Binding.single { Notifications() },
  notificationsLoop = Binding.single {
    periodicServices.create("notifications", configBinder.config("notifications.loop")) { notifications.sendPending() }
  },
)
```

A loop that needs another gate passes it to `create`, in place of the shared one.

## Telemetry

`boot-periodic` can make each run a trace of its own, with `periodicTracing`, a `PeriodicRunInterceptor`: a root span named for the loop, such as `periodic notifications`, so that the run’s queries and log events belong to it. The span carries the loop’s name as `bootable.periodic.service`, and a run that fails ends it with an error status and the exception recorded. A run the gate skips isn’t traced.

It depends only on the OpenTelemetry API, through [`boot-telemetry-otel-common`](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md#spans); the application’s SDK, such as [`boot-telemetry-otel`](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md)'s, records the traces.

**Vanilla:**

Give `periodicTracing` to `PeriodicServices`, or to each `PeriodicAppService`, as an interceptor:

```kotlin
  PeriodicServices(
    PeriodicGate.leader(leaderElection),
    interceptors = listOf(periodicTracing(telemetry.openTelemetry)),  // a trace for each run
  )
```

**Kodein:**

`periodicModule` traces each run whenever there is an `OpenTelemetry` binding, such as `openTelemetryModule`'s, and not at all when there is none:

```kotlin
  import(openTelemetryModule)  // the application's OpenTelemetry, from boot-telemetry-otel: periodicModule traces each run
```

The tracing is the outermost interceptor, around those of the `Set<PeriodicRunInterceptor>`.

**Koin:**

`periodicModule()` traces each run whenever there is an `OpenTelemetry` definition, such as `openTelemetryModule()`'s, and not at all when there is none:

```kotlin
includes(openTelemetryModule())  // the application's OpenTelemetry, from boot-telemetry-otel: periodicModule() traces each run
```

The tracing is the outermost interceptor, around those of the `PeriodicRunInterceptor` definitions.

**Metro:**

`boot-periodic-metro` traces each run whenever the graph has an `OpenTelemetry`, such as `boot-telemetry-otel-metro`'s, and not at all when it has none: there is nothing to wire. The tracing is the outermost interceptor, around the contributed ones.

**Inara:**

`periodicModule()` requires an `OpenTelemetryApiModule`, and traces each run through it: compose `openTelemetryModule()` to trace, or `NoopOpenTelemetryModule` for none:

```kotlin
@DIModule(composed = true)
interface TracedPeriodicAppModule :
  // OpenTelemetryModule in place of OpenTelemetryApiModule: it is one, with the SDK
  ConfigModule, BootableModule, LeaderElectionModule, OpenTelemetryModule, PeriodicModule

fun tracedPeriodicAppModule() = TracedPeriodicAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  leaderElectionModule = AlwaysLeaderModule,
  // the application's OpenTelemetry, from boot-telemetry-otel: periodicModule traces each run
  openTelemetryModule = openTelemetryModule(),
  periodicModule = periodicModule(),
)
```

### Sampling, or no tracing

Each run’s trace is sampled as the SDK’s sampler decides, such as at the configured `sampleRatio`. For a loop that runs rarely enough that every run’s trace is worth keeping, sample them all with `Sample.Always`. With Kodein or Metro, a `PeriodicTracingOptions` binding sets that, with Koin, a `PeriodicTracingOptions` definition, and with Inara, `periodicModule`'s `tracingOptions`; with `enabled = false`, it turns the tracing off:

**Vanilla:**

```kotlin
  PeriodicServices(interceptors = listOf(periodicTracing(telemetry.openTelemetry, Sample.Always)))
```

**Kodein:**

```kotlin
  bindSingleton { PeriodicTracingOptions(sample = Sample.Always) }  // or enabled = false, to trace no run
```

**Koin:**

```kotlin
single { PeriodicTracingOptions(sample = Sample.Always) }  // or enabled = false, to trace no run
```

**Metro:**

Provide it in a binding container that the graph names:

```kotlin
@BindingContainer
object AlwaysSampledPeriodicTracing {
  @Provides fun periodicTracingOptions(): PeriodicTracingOptions =
    PeriodicTracingOptions(sample = Sample.Always)  // or enabled = false
}

@DependencyGraph(
  BackgroundScope::class,
  additionalScopes = [BootScope::class],
  bindingContainers = [AlwaysSampledPeriodicTracing::class],
)
interface AlwaysSampledPeriodicGraph : BootableGraph
```

**Inara:**

The options are a parameter of the builder:

```kotlin
  // a parameter of the builder; or enabled = false, to trace no run
  periodicModule = periodicModule(tracingOptions = PeriodicTracingOptions(sample = Sample.Always)),
```

The setting applies to every loop that shares the interceptor: with Kodein, Koin, Metro, or Inara, every loop created from `PeriodicServices`.

## Reference

- [`PeriodicAppService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-periodic/com.github.rocketraman.bootable.periodic/-periodic-app-service/index.html): the loop
- [`PeriodicConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-periodic/com.github.rocketraman.bootable.periodic/-periodic-config/index.html): its settings
- [`PeriodicGate`](https://rocketraman.github.io/bootable/bootable/next/api/boot-periodic/com.github.rocketraman.bootable.periodic/-periodic-gate/index.html): whether it runs this time round
- [`PeriodicFailureHandler`](https://rocketraman.github.io/bootable/bootable/next/api/boot-periodic/com.github.rocketraman.bootable.periodic/-periodic-failure-handler/index.html): what it does with a run that failed
- [`PeriodicServices`](https://rocketraman.github.io/bootable/bootable/next/api/boot-periodic/com.github.rocketraman.bootable.periodic/-periodic-services/index.html): what the loops share
- [`PeriodicRunInterceptor`](https://rocketraman.github.io/bootable/bootable/next/api/boot-periodic/com.github.rocketraman.bootable.periodic/-periodic-run-interceptor/index.html): code around each run
- [`periodicTracing`](https://rocketraman.github.io/bootable/bootable/next/api/boot-periodic/com.github.rocketraman.bootable.periodic/periodic-tracing.html): a trace for each run
- [`PeriodicTracingOptions`](https://rocketraman.github.io/bootable/bootable/next/api/boot-periodic/com.github.rocketraman.bootable.periodic/-periodic-tracing-options/index.html): how the DI flavours trace the runs
