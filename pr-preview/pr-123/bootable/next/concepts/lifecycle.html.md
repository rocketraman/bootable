# Lifecycle and services

> How Bootable builds, starts, runs, and stops an application: services, lifecycle controllers, states, start failures, and shutdown.

An application is a set of services, each with a start and a stop, and a set of lifecycle controllers, which decide when the services start and stop. `boot` builds the application, and `Bootable` runs it: it starts the services in their [start order](https://rocketraman.github.io/bootable/bootable/next/concepts/start-order.html.md), waits until it is asked to stop, and stops them in reverse.

## The application

An application’s `main` calls `boot { }`, whose block builds the application and returns a `Bootable`. Here, an application with one service, `HelloServer`, a [Ktor server](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md) that answers `hello` on port 8000:

**Vanilla:**

```kotlin
fun main() = boot {
  Bootable(setOf(HelloServer(HostPort(port = 8000))))
}
```

**Kodein:**

`bootableModule` binds `Bootable`, and opens the sets; `bindAppService` adds a service to them:

```kotlin
fun main() = boot {
  val di = DI {
    import(bootableModule)       // Bootable, its default controllers, and the sets to contribute into
    bindAppService { singleton { HelloServer(HostPort(port = 8000)) } }
  }
  di.direct.instance<Bootable>()
}
```

**Koin:**

`bootableModule()` defines `Bootable`, over every `AppService` and `LifecycleController` definition; give each of yours a qualifier of its own:

```kotlin
fun main() = boot {
  val koin = koinApplication {
    modules(
      bootableModule(),  // Bootable, its default controllers, and every AppService and LifecycleController definition
      module { single<AppService>(named<HelloServer>()) { HelloServer(HostPort(port = 8000)) } },
    )
  }.koin
  koin.get<Bootable>()
}
```

**Metro:**

A service contributed into the `Set<AppService>` is in the application; scope it with `@SingleIn`, so the instance that runs is the only one:

```kotlin
@ContributesTo(AppScope::class)
interface HelloBindings {
  @Provides @IntoSet @SingleIn(AppScope::class)
  fun server(): AppService = HelloServer(HostPort(port = 8000))
}

// BootableGraph brings Bootable, its default controllers, and the sets to contribute into
@DependencyGraph(AppScope::class, additionalScopes = [BootScope::class])
interface AppGraph : BootableGraph

fun main() = boot {
  createGraph<AppGraph>().bootable
}
```

**Inara:**

The application’s module is composed of Bootable’s, whose `bootable` runs every member marked `@AppServices`; `BootableModule` requires a `ConfigModule`, here `EmptyConfigModule`, for an application without config:

```kotlin
@DIModule(composed = true)
interface AppModule : ConfigModule, BootableModule {
  @AppServices val helloServer: HelloServer
}

fun main() = boot {
  AppModule(
    configModule = EmptyConfigModule,   // no config: Bootable's defaults
    bootableModule = bootableModule(),  // Bootable, its default controllers, and the sets to mark members into
    helloServer = Binding.single { HelloServer(HostPort(port = 8000)) },
  ).bootable
}
```

`boot` initializes logging and then delegates to the passed block to return a `Bootable` with the application’s services and lifecycle controllers (or defaults) injected. `boot` then runs the `Bootable` it returned. `Bootable` starts all the registered services and runs until it is told to stop, generally through a process signal or other internal or external shutdown signal, and then shuts down services cleanly and exits the process with the application’s exit code.

Both [`boot`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/boot.html) and [`Bootable`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-bootable/index.html) take optional parameters for additional control over logging, shutdown, and lifecycle.

`boot` runs the application on the main thread, in `runBlocking`. What it calls, `Bootable`'s `run`, is a suspending function that returns the exit code rather than exiting, so a test, or a host that embeds an application, can run one in a coroutine, and stop it with `Bootable`'s `stop`.

## Services

A service is anything with a lifecycle: a connection pool, a server, a background loop. It implements `AppService`, as `HelloServer` does, which runs a Ktor server while the application runs:

```kotlin
class HelloServer(private val config: HostPort) : AppService {
  private lateinit var server: EmbeddedServer<*, *>

  override suspend fun start() {
    server = embeddedServer(CIO, port = config.port(8080)) {
      routing {
        get("/") { call.respondText("hello") }
      }
    }
    server.startSuspend()
  }

  override suspend fun shutdown() {
    server.stopSuspend(gracePeriodMillis = 50, timeoutMillis = 1_000)
  }

  override fun ordering() = Ordering(DefaultPhase.Serving)
}
```

**`start`**

Acquires what the service runs with, and returns once it is running. Here, it binds the port and starts the engine. Services start one at a time, so a `start` that takes long holds up the rest. A service with background work of its own, such as a loop, overrides `start(scope)` instead, and launches that work in the scope it gets, the service’s own.

**`shutdown`**

Releases it: here, it stops the engine, letting the requests in flight finish. It is called only if `start` returned. A service needn’t stop what it launched in its scope: once `shutdown` returns, Bootable cancels the scope, and waits for its coroutines.

**`ordering`**

Where the service starts, relative to the others; see [Start order](https://rocketraman.github.io/bootable/bootable/next/concepts/start-order.html.md). A server starts in the `Serving` phase, once the services it uses have started, and stops first. The default suits most of an application’s own services.

**`name`**

The service’s name in the logs and the start plan, its class’s simple name by default.

Constructing a service and starting it are separate steps. The constructor takes what the service uses, and does nothing with it; `start` opens connections, binds ports, and starts threads. So building the application has no side effects: a test can construct it, and check its wiring and its start plan, without starting anything.

An application needn’t write a server this way: [`boot-server-http-ktor`](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md)'s `CioKtorService` and `NettyKtorService` do all of this, so a server extends one, and writes only its Ktor module, as the [Ktor server](https://rocketraman.github.io/bootable/bootable/next/examples/server-ktor.html.md) example does, on Netty.

`start` and `shutdown` are suspending, and run on the lifecycle’s own thread, the main thread under `boot`, one service at a time. The scope that `start(scope)` gets is a child of the application’s scope, on `Dispatchers.Default`, so the work a service launches there runs on that dispatcher. Wrap blocking calls in them, such as JDBC or file IO, in `withContext(Dispatchers.IO)`. Bootable stops a service’s work by cancelling it, so code that catches `Exception` must not swallow a `CancellationException`; the [`AppService`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-app-service/index.html) reference shows how to rethrow it.

A service’s scope is a child of the application’s, so nothing a service launches there outlives the application. Don’t launch background work anywhere else, such as in `GlobalScope`, or a scope the service makes itself: nothing stops that work when the application stops, and its failure doesn’t stop the application.

### When a service fails while it runs

A coroutine that fails in a service’s scope, such as a server whose engine stops, fails the service. Bootable cancels the service’s other coroutines, marks it `Failed`, and stops the application, in order, with exit code 1. A service that notices outside its scope, such as in a request handler, that it can’t go on, fails it with `scope.launch { throw e }`.

The service’s `scopeFailurePolicy` changes that. `ScopeFailurePolicy.StopApplication(exitCode)` stops the application with another exit code. `ScopeFailurePolicy.LogAndContinue` suits a service whose coroutines are independent of each other, such as fire-and-forget work launched from request handlers: a coroutine that fails is logged, and the service and the application carry on.

## Lifecycle controllers

A lifecycle controller decides when services start and stop, and can run code around the application’s lifecycle. `defaultLifecycleControllers()` has three:

**`AppStartStopLifecycleController`**

Starts every service, in the start plan’s order, when the application starts, and stops them in reverse when it shuts down.

**`StopSignalHandlerLifecycleController`**

Shuts the application down on `HUP`, `INT`, or `TERM`, such as Ctrl-C or a container’s stop.

**`HealthLifecycleController`**

Tracks the application’s state, for [liveness and readiness](https://rocketraman.github.io/bootable/bootable/next/http/health.html.md).

A controller implements `LifecycleController`, whose methods all do nothing by default, and those that may do IO suspend:

- `appInit`, before any service starts.
- `appStart`, to start the application, with a callback that stops it later.
- `aroundAppStart`, to run code around the whole start, and `aroundServiceStart`, around each service’s.
- `appRunning`, once the whole start has completed.
- `serviceStartVote`, to keep a service from starting.
- `appShutdown` and `appCleanup`, as the application stops.

A controller’s `priority` orders it against the other controllers; it has nothing to do with the order the services start in, which is the [start plan](https://rocketraman.github.io/bootable/bootable/next/concepts/start-order.html.md). A higher priority’s `appInit` and `appStart` run first, its `aroundAppStart` and `aroundServiceStart` are outermost, and its `appShutdown` runs last. `AppStartStopLifecycleController` has the highest priority, so its `appStart` starts the services before any other controller’s runs, and its `appShutdown` stops them after every other controller’s; `HealthLifecycleController` has the lowest, so readiness goes down before anything else stops.

Bootable’s own modules add controllers this way. [`HealthLifecycleController`](https://rocketraman.github.io/bootable/bootable/next/http/health.html.md) tracks the application’s state, and waits for load balancers when shutdown begins. [`ServiceStartTracing`](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md#start-tracing) traces the start, as a span around the application’s start, from `aroundAppStart`, and one around each service’s, from `aroundServiceStart`.

### Turning services off and on

The default controllers start everything at boot, but that is a choice, not a rule. `appStart` receives the services, and a `serviceStart` and a `serviceShutdown` that start and stop any one of them, at any time while the application runs. Every start, at boot or later, first asks each controller’s `serviceStartVote`, and a service that any controller votes `NO` on doesn’t start.

So a controller of your own can switch services off and on while the process runs: it votes `NO` on the disabled ones, and stops or starts a service as it is disabled or enabled. Which are disabled can come from config that reloads, such as a Kubernetes ConfigMap, or from commands, such as messages on a topic, to stop a queue consumer during maintenance.

## States

The application is in one of four states, which `HealthLifecycleController` tracks:

**`Starting`**

From the start, until the whole start has completed.

**`Running`**

Every service has started, and so has every controller.

**`Stopping`**

Shutdown has begun. A start that fails goes here too.

**`Stopped`**

Every service has stopped.

Liveness and readiness are answered from them: the application is live until it has stopped, and ready only while it is running, and its readiness checks pass. See [Health](https://rocketraman.github.io/bootable/bootable/next/http/health.html.md).

Each service has a state of its own too, a `ServiceState`: `Pending`, `Starting`, `Running`, `Skipped` (a controller voted against its start), `Failed`, `Stopping`, or `Stopped`. Controllers see them in the `ServiceContext`, and the health reports include them.

## The start

`Bootable` logs each step of the start as it goes:

1. `⇒ Creating application`: `boot` runs the passed block, which decodes the config and constructs the services.
2. `Start plan: …​`: it computes the order the services start in, and logs it; see [the start plan](https://rocketraman.github.io/bootable/bootable/next/concepts/start-order.html.md#start-plan).
3. `⇒ Initializing`: each controller’s `appInit`.
4. `⇒ Starting`: each controller’s `appStart`, which, for `AppStartStopLifecycleController`, starts each service in turn, logged as `=⇒ Starting HelloServer…`.
5. `⇒ Running`: every service has started, and the signal handlers are installed.

## When the start fails

The application fails as early as it can, and stops what it started:

- Config that doesn’t decode, or a service that can’t be constructed, fails in `boot`'s block, before anything has started.
- A start order that can’t be met fails before any controller runs; see [validation](https://rocketraman.github.io/bootable/bootable/next/concepts/start-order.html.md#validation).
- A service whose `start` throws fails the start. The services that had started are stopped, in reverse, and the one that failed isn’t, since its `start` never returned: a service that must release what it acquired before it failed does so in `start`, before it throws.

Either way, the failure is logged, and the process exits with -1.

A stop asked for during the start, such as by a signal, cancels the start, at the starting service’s next suspension point, and the services that had started are stopped.

## Shutdown

The application stops when it is asked to:

- on `HUP`, `INT`, or `TERM`, by `StopSignalHandlerLifecycleController`, with exit code 0;
- when a service fails while it runs, with exit code 1, unless its [`scopeFailurePolicy`](https://rocketraman.github.io/bootable/bootable/next/concepts/lifecycle.html.md#failure) says otherwise;
- when code calls `Bootable`'s `stop`, with the exit code it gives;
- by a JVM shutdown hook, as a fallback, if the JVM begins to exit while the application runs, which waits for the stop.

The first of these counts, and any after it change nothing. The signal handlers are installed once every service has started; until then, a signal reaches the JVM, whose shutdown hook stops the application.

Shutdown is logged as `⇒ Shutting down`. Readiness goes down first, and `HealthLifecycleController` waits for its [drain delay](https://rocketraman.github.io/bootable/bootable/next/http/health.html.md#drain), if there is one, so load balancers stop routing to the application before its server stops. Then the services stop, in the reverse of the start plan, each logged as `=⇒ Stopping HelloServer…`, so a server stops before what it uses, and the connection pools close once nothing uses them; see [the shutdown order](https://rocketraman.github.io/bootable/bootable/next/concepts/start-order.html.md#shutdown). A service that throws as it stops is logged, and the rest still stop.

Once a service has stopped, Bootable cancels its scope, and waits for its coroutines.

The services have `maxShutdownTime`, 30 seconds by default, to stop. After that, Bootable cancels the stop: the service stopping is cancelled, and those not yet stopped are skipped. It logs each service’s state, the coroutines still running, and the threads' stacks, to show what is stuck, and exits with exit code 1. A service blocked in code that ignores cancellation, such as a blocking call, doesn’t return when it is cancelled, so if the stop still hasn’t returned 5 seconds later, Bootable halts the process. The drain delay comes before this timeout starts, so a platform’s grace period, such as Kubernetes’s `terminationGracePeriodSeconds`, must cover both.

When every service has stopped, it logs `⇒ Stopped`, and the process exits.

## Reference

- [`boot`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/boot.html): the entry point
- [`Bootable`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-bootable/index.html): the runtime
- [`AppService`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-app-service/index.html): a service
- [`ScopeFailurePolicy`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-scope-failure-policy/index.html): what a failure in a service’s scope does
- [`LifecycleController`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-lifecycle-controller/index.html): a controller
- [`AppStartStopLifecycleController`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-app-start-stop-lifecycle-controller/index.html), [`StopSignalHandlerLifecycleController`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-stop-signal-handler-lifecycle-controller/index.html), and [`HealthLifecycleController`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-health-lifecycle-controller/index.html): the default controllers
- [`LifecycleState`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-lifecycle-state/index.html) and [`ServiceState`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-service-state/index.html): the states
