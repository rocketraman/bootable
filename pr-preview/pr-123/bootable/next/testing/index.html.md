# Testing

> Helpers for unit tests: a test clock, captured logs, start plans, and in-memory telemetry, without booting the application.

`boot-test` and `boot-test-otel` are helpers for unit tests, in plain Kotlin, so JUnit, Kotest, and TestBalloon can all use them. `boot-test` has a test clock, captured logs, a check of the start plan, and config for a test; `boot-test-otel` collects the spans and metrics an OpenTelemetry SDK records, in memory. An application adds them as test dependencies.

They deliberately don’t boot the application.

> **Note:** Bootable won’t add a way to boot the application in tests. Where one exists, as in the Spring Boot ecosystem, it tends to become the way every test is written, because it is the easy way out: nobody has to think about injecting fakes. That makes tests slow and brittle, and replaces unit tests rather than complementing them. If this style of testing makes sense for your project, it is easy to implement yourself.

Instead, a test constructs what it tests, as the wiring does:

- **A class**: construct it, with a fake for each thing it uses.
- **A Ktor server’s routes**: run its module in Ktor’s `testApplication`, with the server constructed the same way.
- **The wiring**: build the application’s graph, or call its vanilla wiring, and check the services it builds and their start plan, without starting anything.

## Modules

| Module | What it provides |
| --- | --- |
| **Test helpers: optional, as test dependencies** |  |
| `boot-test` | Helpers for unit tests, such as a test clock, captured logs, and the start plan, without booting the application |
| `boot-test-otel` | An in-memory OpenTelemetry SDK, to check a component’s spans and metrics |

## Install

Add them as test dependencies, in the flavour of your wiring style:

**Vanilla:**

```kotlin
dependencies {
  testImplementation("com.github.rocketraman.bootable:boot-test:3.1.0-SNAPSHOT")
  testImplementation("com.github.rocketraman.bootable:boot-test-otel:3.1.0-SNAPSHOT")
}
```

**Kodein:**

```kotlin
dependencies {
  testImplementation("com.github.rocketraman.bootable:boot-test:3.1.0-SNAPSHOT")
  testImplementation("com.github.rocketraman.bootable:boot-test-otel-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

```kotlin
dependencies {
  testImplementation("com.github.rocketraman.bootable:boot-test:3.1.0-SNAPSHOT")
  testImplementation("com.github.rocketraman.bootable:boot-test-otel-koin:3.1.0-SNAPSHOT")
}
```

**Metro:**

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  testImplementation("com.github.rocketraman.bootable:boot-test:3.1.0-SNAPSHOT")
  testImplementation("com.github.rocketraman.bootable:boot-test-otel-metro:3.1.0-SNAPSHOT")
}
```

**Inara:**

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
  testImplementation("com.github.rocketraman.bootable:boot-test:3.1.0-SNAPSHOT")
  testImplementation("com.github.rocketraman.bootable:boot-test-otel-inara:3.1.0-SNAPSHOT")
}
```

`boot-test` has no flavours, since nothing in it is wired; `boot-test-otel` has `-kodein`, `-koin`, `-metro`, and `-inara` flavours, for its [module and bindings](https://rocketraman.github.io/bootable/bootable/next/testing/index.html.md#telemetry).

## Test a class

The examples on this page test this class, which keeps sessions that expire, logs and traces each one it opens, and counts them:

```kotlin
/** Opens sessions that expire after [ttl], logging and tracing each one, and counting them. */
class Sessions(private val clock: Clock, openTelemetry: OpenTelemetry, private val ttl: Duration = 30.minutes) {
  private val log = logger()
  private val tracer = openTelemetry.getTracer("sessions")
  private val opened = openTelemetry.getMeter("sessions").counterBuilder("sessions.opened").build()
  private val sessions = ConcurrentHashMap<String, Session>()

  fun open(user: String): Session {
    val span = tracer.spanBuilder("open session").startSpan()
    try {
      val session = Session("${user}-${sessions.size}", user, clock.now() + ttl)
      sessions[session.id] = session
      opened.add(1)
      log.info { "Opened session ${session.id}" }
      return session
    } finally {
      span.end()
    }
  }

  fun isValid(session: Session): Boolean = clock.now() < session.expiresAt

  /** Forgets the expired sessions. */
  fun reap() {
    sessions.values.removeIf { !isValid(it) }
  }
}
```

The examples are functions a test calls, with Kotlin’s [power-assert](https://kotlinlang.org/docs/power-assert.html).

Construct the class as the wiring does, with a fake for each thing it uses: here a `TestClock`, and OpenTelemetry’s no-op `OpenTelemetry`.

```kotlin
fun sessionsAreValid() {
  // constructed as the wiring constructs it, with a fake for each thing it uses
  val sessions = Sessions(TestClock(), OpenTelemetry.noop())

  val session = sessions.open("ada")
  assert(session.user == "ada")
  assert(sessions.isValid(session))
}
```

### The clock

`TestClock` is a `kotlin.time.Clock` that stands still until the test moves it, with `advanceBy` or `set`. It starts at `TestClock.DEFAULT_START`, 2026-01-01T00:00:00Z, so a test’s times are the same on every run, and it is safe to read and move from any thread.

```kotlin
fun sessionsExpire() {
  val clock = TestClock()  // stands still at TestClock.DEFAULT_START
  val sessions = Sessions(clock, OpenTelemetry.noop(), ttl = 30.minutes)
  val session = sessions.open("ada")

  clock.advanceBy(29.minutes)
  assert(sessions.isValid(session))
  clock.advanceBy(1.minutes)
  assert(!sessions.isValid(session))
}
```

Pass it to whatever takes a `Clock`, such as `OpenTelemetryService`, `ServiceStartTracing`, or `PeriodicAppService`, or, with Kodein, Koin, or Metro, bind it as the `Clock`, which they then take their time from; with Inara, which has no optional bindings, pass it to each module’s builder, such as `openTelemetryModule(clock)`. For a monotonic `TimeSource`, use the standard library’s `kotlin.time.TestTimeSource`.

### Logs

`captureLogs(loggerName) { …​ }` runs the block, and returns the Log4j2 events logged meanwhile to that logger and its descendants, the root logger by default:

```kotlin
fun openingASessionIsLogged() {
  val sessions = Sessions(TestClock(), OpenTelemetry.noop())

  val logs = captureLogs("snippets.testing") {  // the logger, and its descendants
    sessions.open("ada")
  }
  assert(logs.messages(Level.INFO) == listOf("Opened session ada-0"))
  // the one matching event, or an AssertionError that lists what was captured
  val event = logs.single(Level.INFO) { "ada" in it.message.formattedMessage }
  assert(event.loggerName == Sessions::class.qualifiedName)
}
```

The result is a `LogCapture`, closed, with these to assert on:

- `events`: every event captured, in the order they were logged, and `events(level) { …​ }`, those at a level that match.
- `messages(level)`: the formatted messages of the events at a level, or at any level.
- `single(level) { …​ }`: the one event that matches, or an `AssertionError` that lists what was captured.
- `describe()`: what was captured, one event per line, such as for an assertion’s message.

Each event is Log4j2’s own `LogEvent`, copied, with its level, logger, message, thrown, and context data, such as the trace and span ids that `boot-telemetry-otel` puts there. `fields` has the structured fields of a map message, such as a [`StructuredMessage`](https://rocketraman.github.io/bootable/bootable/next/logging/index.html.md#fields), as strings.

A `LogCapture` captures from when it is created until it is closed, so a test can use it around more than one block. It captures what the logger’s level lets through; a `level` lowers the logger’s level while it captures, such as to `Level.DEBUG`, and its other appenders then get those events too:

```kotlin
fun reapingIsQuiet() {
  val sessions = Sessions(TestClock(), OpenTelemetry.noop())
  LogCapture("snippets.testing", level = Level.DEBUG).use { logs ->  // DEBUG too, while it captures
    sessions.reap()
    assert(logs.events.isEmpty()) { logs.describe() }
  }
}
```

Both initialize Bootable’s logging for the test first, with `testLoggingInit()`, as `boot` does for an application: Bootable’s base Log4j2 configuration, merged with the test’s own `log4j2.xml`, if it has one, at the level of `LOGGING_LEVEL`, or `logging.level`, or `INFO`. Unlike `boot`, it leaves standard out and error alone, so the test framework’s output is too. A test that logs without capturing can call `testLoggingInit()` itself; only the first call has any effect. See [Logging (Log4j2)](https://rocketraman.github.io/bootable/bootable/next/logging/index.html.md) for the configuration it loads.

### Failures in suspending code

A service’s `start` and `shutdown` suspend, so a test that checks that one fails calls it in a coroutine. `runSuspendCatching { …​ }` is `runCatching` for a block that suspends, such as to assert that a service’s start fails. It catches what the block throws, but not the cancellation of the test’s own coroutine, which `runCatching` would swallow, so a cancelled test stops rather than carrying on.

## Test a Ktor server’s routes

Run the server’s module in Ktor’s `testApplication`, from `io.ktor:ktor-server-test-host`, with the server constructed as the wiring constructs it, and its dependencies either fakes or real ones the test starts by hand. The test’s `client` then calls the routes without opening a port. `captureLogs` and `InMemoryTelemetry` work there as anywhere else.

`examples/server-ktor-db` tests its routes this way, against a SQLite database in a temporary directory; see [Example](https://rocketraman.github.io/bootable/bootable/next/testing/index.html.md#example).

## Test the wiring

A test checks the wiring without starting anything: it builds the services as the application does, and checks which services there are, and their start plan. The application’s wiring is written so that `main` and the test share it:

**Vanilla:**

A class that constructs the services from the config:

```kotlin
/** The application's wiring, which `main` and the tests share. */
class SessionsApp(binder: ConfigBinder, clock: Clock = Clock.System) {
  val telemetry = OpenTelemetryService(telemetryConfig(binder), clock = clock)
  val sessions = Sessions(clock, telemetry.openTelemetry)
  val services: Set<AppService> = setOf(telemetry, SessionReaper(sessions))
}

fun main() = boot {
  Bootable(SessionsApp(hopliteConfigBinder()).services)
}
```

**Kodein:**

A module of the application’s own bindings, and a function that builds the `DI`:

```kotlin
/** The application's own bindings, which `main` and the tests share. */
val sessionsModule by DI.Module {
  bindSingleton { Sessions(instance(), instance()) }
  inBindSet<AppService> { add { singleton { SessionReaper(instance()) } } }
}

fun appDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(openTelemetryModule)
  import(sessionsModule)
  bindSingleton<Clock> { Clock.System }
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
```

**Koin:**

A module of the application’s own definitions, and a function that builds the Koin application:

```kotlin
/** The application's own definitions, which `main` and the tests share. */
val sessionsModule = module {
  single { Sessions(get(), get()) }
  single<AppService>(named<SessionReaper>()) { SessionReaper(get()) }
}

fun appKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    openTelemetryModule(),
    sessionsModule,
    module { single<Clock> { Clock.System } },
  )
}.koin

fun main() = boot {
  appKoin().get<Bootable>()
}
```

**Metro:**

A binding container of the application’s own bindings, and the graph:

```kotlin
/** The application's own bindings, which the application's graph and the tests' share. */
@ContributesTo(SessionsScope::class) @BindingContainer
object SessionsBindings {
  @Provides @SingleIn(SessionsScope::class)
  fun sessions(clock: Clock, openTelemetry: OpenTelemetry): Sessions = Sessions(clock, openTelemetry)

  @Provides @IntoSet fun sessionReaper(sessions: Sessions): AppService = SessionReaper(sessions)
}

@DependencyGraph(SessionsScope::class, additionalScopes = [BootScope::class])
interface AppGraph : BootableGraph {
  @Provides fun clock(): Clock = Clock.System
}

fun main() = boot {
  createGraph<AppGraph>().bootable
}
```

**Inara:**

A partial module of the application’s own members, with a builder function, and the composed module:

```kotlin
/** The application's own module, which `main` and the tests share. */
@DIModule
interface SessionsModule : @DIModule.Required OpenTelemetryApiModule {
  val sessions: Sessions
  @AppServices val sessionReaper: SessionReaper
}

/** The clock is a setting: a parameter, which a test passes. */
fun sessionsModule(clock: Clock = Clock.System) = SessionsModulePart(
  sessions = Binding.single { Sessions(clock, openTelemetry) },
  sessionReaper = Binding.single { SessionReaper(sessions) },
)

@DIModule(composed = true)
interface AppModule : ConfigModule, BootableModule, OpenTelemetryModule, SessionsModule

fun appModule() = AppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),
  sessionsModule = sessionsModule(),
)

fun main() = boot {
  appModule().bootable
}
```

The test builds the services the same way:

**Vanilla:**

```kotlin
fun theWiringResolves() {
  // nothing is started: constructing the services is enough
  val services = SessionsApp(hopliteConfigBinder(report = false)).services
  assert(services.map { it::class }.toSet() == setOf(OpenTelemetryService::class, SessionReaper::class))

  checkShuffledStartPlans(services) { plan ->
    assert(plan.indexOfService<OpenTelemetryService>() < plan.indexOfService<SessionReaper>())
  }
}
```

**Kodein:**

```kotlin
fun theWiringResolves() {
  // nothing is started: resolving the services is enough
  val services = appDI().direct.instance<Set<AppService>>()
  assert(services.map { it::class }.toSet() == setOf(OpenTelemetryService::class, SessionReaper::class))

  checkShuffledStartPlans(services) { plan ->
    assert(plan.indexOfService<OpenTelemetryService>() < plan.indexOfService<SessionReaper>())
  }
}
```

**Koin:**

```kotlin
fun theWiringResolves() {
  // nothing is started: resolving the services is enough
  val services = appKoin().getAll<AppService>().toSet()
  assert(services.map { it::class }.toSet() == setOf(OpenTelemetryService::class, SessionReaper::class))

  checkShuffledStartPlans(services) { plan ->
    assert(plan.indexOfService<OpenTelemetryService>() < plan.indexOfService<SessionReaper>())
  }
}
```

**Metro:**

With Metro, the compiler has already checked that the graph is complete; the test checks what it builds.

```kotlin
fun theWiringResolves() {
  // nothing is started: creating the graph is enough, and the compiler has checked it is complete
  val services = createGraph<AppGraph>().appServices
  assert(services.map { it::class }.toSet() == setOf(OpenTelemetryService::class, SessionReaper::class))

  checkShuffledStartPlans(services) { plan ->
    assert(plan.indexOfService<OpenTelemetryService>() < plan.indexOfService<SessionReaper>())
  }
}
```

**Inara:**

```kotlin
fun theWiringResolves() {
  // nothing is started: building the module is enough, and the compiler has checked it is complete
  val services = appModule().setOfAppServices()
  assert(services.map { it::class }.toSet() == setOf(OpenTelemetryService::class, SessionReaper::class))

  checkShuffledStartPlans(services) { plan ->
    assert(plan.indexOfService<OpenTelemetryService>() < plan.indexOfService<SessionReaper>())
  }
}
```

The config comes from the test’s resources, as it does for the application. Constructing `OpenTelemetryService` builds the SDK, so a test’s config doesn’t register it as the global `OpenTelemetry`, and leaves out the runtime metrics:

```hocon
telemetry.sdk {
  registerGlobal = false   (1)
  metrics.runtime = false  (2)
}
```

- (1) Keeps each test’s SDK out of the process’s `GlobalOpenTelemetry`, which only the first SDK in a process can be, so a test doesn’t depend on which test ran first.
- (2) Leaves out the JVM’s runtime metrics, which a test doesn’t check.

A test that wires the application with a DI flavour can do without config files: `StaticConfigBinder` is a `ConfigBinder` that the test gives its config sections to, already decoded, by prefix, such as `StaticConfigBinder("health.ktor" to KtorHealthConfig(hostPort = "127.0.0.1:0"))`. Asked for a section it doesn’t have, or for one of another type, it fails, naming the prefix.

### The start plan

`checkShuffledStartPlans(services) { plan → …​ }` computes the services' start plan, the order Bootable would start them in, and runs the check on it, without starting anything. It does so twenty times, by default, each with the ties between services that their orderings leave unordered broken by a different `StartOrder.Tiebreaker.Shuffled`. A service that relies on an order its ordering doesn’t declare fails the check in some of them.

A check that fails is rethrown as an `AssertionError` that names the tiebreaker’s seed and lists the plan, so `startPlan(services, tiebreaker = StartOrder.Tiebreaker.Shuffled(seed))` replays it. `rounds`, `startOrder`, and `random` change how many plans it checks, the start order they follow, and where the seeds come from.

`indexOfService<T>()` is where the first service of type `T` is in a plan, or -1 if there is none.

## Telemetry

`InMemoryTelemetry`, from `boot-test-otel`, collects every span and metric an OpenTelemetry SDK records, in memory, for a test to assert on. It is an `OpenTelemetryCustomizer`: it adds OpenTelemetry’s in-memory span exporter and metric reader to the SDK, whose other exporters still get everything too. Put it in the SDK’s customizers, and keep it to assert on:

**Vanilla:**

Pass it to the `OpenTelemetryService` the class records through:

```kotlin
suspend fun openingASessionIsTracedAndCounted() {
  val telemetry = InMemoryTelemetry()
  val clock = TestClock()
  val otel = OpenTelemetryService(
    TelemetryConfig(registerGlobal = false, metrics = MetricsConfig(runtime = false)),
    listOf(telemetry),  // the in-memory telemetry is one of the SDK's customizers
    clock,
  )
  val sessions = Sessions(clock, otel.openTelemetry)

  sessions.open("ada")
  val span = telemetry.spans("open session").single()
  assert(span.startEpochNanos == clock.now().toEpochMilliseconds() * 1_000_000)
  assert(telemetry.metric("sessions.opened")!!.longSumData.points.single().value == 1L)
  otel.shutdown()
}
```

**Kodein:**

Import `inMemoryTelemetryModule` after `openTelemetryModule`, which opens the customizers' set, and get the `InMemoryTelemetry` from the `DI`:

```kotlin
fun openingASessionIsTracedAndCounted() {
  val di = DI {
    import(bootableModule)
    import(hopliteConfigModule)
    import(openTelemetryModule)
    import(inMemoryTelemetryModule)  // after openTelemetryModule, which opens the customizers' set
    import(sessionsModule)
    bindSingleton<Clock> { TestClock() }
  }.direct
  val telemetry = di.instance<InMemoryTelemetry>()

  di.instance<Sessions>().open("ada")
  assert(telemetry.spans("open session").size == 1)
  assert(telemetry.metric("sessions.opened")!!.longSumData.points.single().value == 1L)
}
```

**Koin:**

Include `inMemoryTelemetryModule()` with `openTelemetryModule()`, which collects every customizer, and get the `InMemoryTelemetry` from Koin:

```kotlin
fun openingASessionIsTracedAndCounted() {
  val koin = koinApplication {
    modules(
      bootableModule(),
      hopliteConfigModule(),
      openTelemetryModule(),
      inMemoryTelemetryModule(),  // its InMemoryTelemetry, one of openTelemetryModule()'s customizers
      sessionsModule,
      module { single<Clock> { TestClock() } },
    )
  }.koin
  val telemetry = koin.get<InMemoryTelemetry>()

  koin.get<Sessions>().open("ada")
  assert(telemetry.spans("open session").size == 1)
  assert(telemetry.metric("sessions.opened")!!.longSumData.points.single().value == 1L)
}
```

**Metro:**

Include `InMemoryTelemetryBindings` through the test graph’s factory, which adds its `telemetry` to the customizers, and keep the `InMemoryTelemetry` you pass it:

```kotlin
/** The application's bindings, with the in-memory telemetry and a clock the test passes in. */
@DependencyGraph(SessionsScope::class, additionalScopes = [BootScope::class])
interface SessionsTestGraph : BootableGraph {
  val sessions: Sessions

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Includes telemetry: InMemoryTelemetryBindings, @Provides clock: Clock): SessionsTestGraph
  }
}

fun openingASessionIsTracedAndCounted() {
  val telemetry = InMemoryTelemetry()
  val graph = createGraphFactory<SessionsTestGraph.Factory>().create(InMemoryTelemetryBindings(telemetry), TestClock())

  graph.sessions.open("ada")
  assert(telemetry.spans("open session").size == 1)
  assert(telemetry.metric("sessions.opened")!!.longSumData.points.single().value == 1L)
}
```

**Inara:**

Compose `inMemoryTelemetryModule()`, which marks the `InMemoryTelemetry` as one of the customizers, and read it from the module:

```kotlin
@DIModule(composed = true)
interface SessionsTestModule :
  ConfigModule, BootableModule, OpenTelemetryModule, InMemoryTelemetryModule, SessionsModule

fun openingASessionIsTracedAndCounted() {
  val module = SessionsTestModule(
    configModule = hopliteConfigModule(),
    bootableModule = bootableModule(),
    openTelemetryModule = openTelemetryModule(),
    inMemoryTelemetryModule = inMemoryTelemetryModule(),  // one of the SDK's customizers
    sessionsModule = sessionsModule(clock = TestClock()),
  )
  val telemetry = module.inMemoryTelemetry

  module.sessions.open("ada")
  assert(telemetry.spans("open session").size == 1)
  assert(telemetry.metric("sessions.opened")!!.longSumData.points.single().value == 1L)
}
```

With Kodein, Koin, Metro, and Inara, the SDK’s config comes from the test’s resources, as in [Test the wiring](https://rocketraman.github.io/bootable/bootable/next/testing/index.html.md#wiring).

- `spans`: the spans that have ended so far, in the order they ended, and `spans(name)`, those with a name.
- `metrics()`: every metric recorded so far, collected now, and `metric(name)`, the one with a name, or null.
- `resetSpans()`: forgets the spans ended so far, such as those of what the test set up, so it sees only its own.
- `spanExporter` and `metricReader`: OpenTelemetry’s in-memory exporter and reader, for anything else.

A `TestClock`, passed to `OpenTelemetryService` or bound as the `Clock`, is the SDK’s clock, so the spans' and measurements' times are the test’s. See [telemetry:index.adoc#customizers](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md#customizers) for the SDK’s customizers.

## Example

`examples/server-ktor-db` is tested as this page describes, without booting it:

- `common/src/test/kotlin/EntryServerTest.kt` runs the server’s routes in `testApplication`, against a SQLite database in a temporary directory, with the services it needs started by hand, and captures the server’s log with `captureLogs`.
- `vanilla/src/test/kotlin/AppTest.kt` checks the vanilla wiring’s services, and its start plan with `checkShuffledStartPlans`: the seed entries after the migrations, and before the server.
- `kodein/src/test/kotlin/AppTest.kt`, `koin/src/test/kotlin/AppTest.kt`, and `metro/src/test/kotlin/AppTest.kt` check the services that the Kodein `DI`, the Koin application, and the Metro graph build.

From the repository root, run yours:

**Vanilla:**

```shell
./gradlew :examples:server-ktor-db:common:test :examples:server-ktor-db:vanilla:test
```

**Kodein:**

```shell
./gradlew :examples:server-ktor-db:common:test :examples:server-ktor-db:kodein:test
```

**Koin:**

```shell
./gradlew :examples:server-ktor-db:common:test :examples:server-ktor-db:koin:test
```

**Metro:**

```shell
./gradlew :examples:server-ktor-db:common:test :examples:server-ktor-db:metro:test
```

**Inara:**

```shell
./gradlew :examples:server-ktor-db:common:test :examples:server-ktor-db:inara:test
```

## Reference

- [`TestClock`](https://rocketraman.github.io/bootable/bootable/next/api/boot-test/com.github.rocketraman.bootable.test/-test-clock/index.html): the test clock
- [`captureLogs`](https://rocketraman.github.io/bootable/bootable/next/api/boot-test/com.github.rocketraman.bootable.test/capture-logs.html): captures what a block logs
- [`LogCapture`](https://rocketraman.github.io/bootable/bootable/next/api/boot-test/com.github.rocketraman.bootable.test/-log-capture/index.html): captures logs until it is closed
- [`testLoggingInit`](https://rocketraman.github.io/bootable/bootable/next/api/boot-test/com.github.rocketraman.bootable.test/test-logging-init.html): initializes Bootable’s logging for a test
- [`checkShuffledStartPlans`](https://rocketraman.github.io/bootable/bootable/next/api/boot-test/com.github.rocketraman.bootable.test/check-shuffled-start-plans.html): checks the start plan under shuffled tiebreakers
- [`runSuspendCatching`](https://rocketraman.github.io/bootable/bootable/next/api/boot-test/com.github.rocketraman.bootable.test/run-suspend-catching.html): `runCatching`, for suspending code
- [`StaticConfigBinder`](https://rocketraman.github.io/bootable/bootable/next/api/boot-test/com.github.rocketraman.bootable.test/-static-config-binder/index.html): config sections, already decoded, for a test
- [`InMemoryTelemetry`](https://rocketraman.github.io/bootable/bootable/next/api/boot-test-otel/com.github.rocketraman.bootable.test/-in-memory-telemetry/index.html): the in-memory telemetry
