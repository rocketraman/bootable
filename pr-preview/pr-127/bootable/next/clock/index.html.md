# Clock

> boot-clock: the application's clock, bound once, as a kotlin.time.Clock and a java.time.Clock, for a test to replace with its own.

Code that reads the time takes a clock, rather than reading the system’s, so that a test can set the time. `boot-clock` binds the application’s clock once, for the application’s code and Bootable’s modules to share, so an application doesn’t bind its own, and a test replaces it in one place.

It binds the clock as a `kotlin.time.Clock`, which Bootable’s modules take, and as a `java.time.Clock`, which many Java libraries take. The `java.time.Clock` wraps the `kotlin.time.Clock`, in UTC, so a test that replaces the `kotlin.time.Clock` replaces both.

## Modules

| Module | What it provides |
| --- | --- |
| `boot-clock` | The application’s clock, as a `kotlin.time.Clock` and a `java.time.Clock`, bound once, for a test to replace |

## Install

**Vanilla:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-clock:3.1.0-SNAPSHOT")
}
```

**Kodein:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-clock-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-clock-koin:3.1.0-SNAPSHOT")
}
```

**Metro:**

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-clock-metro:3.1.0-SNAPSHOT")
}
```

**Inara:**

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-clock-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

## The clocks

The clock is `Clock.System`, unless the application or a test gives another, such as boot-test’s [`TestClock`](https://rocketraman.github.io/bootable/bootable/next/testing/index.html.md#clock).

Its `java.time.Clock` is `asJavaClock()` of the `kotlin.time.Clock`, in UTC unless given another zone. It wraps the `kotlin.time.Clock` rather than copying its time, so it always tells the time the `kotlin.time.Clock` does, such as a `TestClock` a test has moved. `Clock.System` is `java.time.Clock.systemUTC()`. `asKotlinClock()` wraps a `java.time.Clock` the same way, such as a `java.time.Clock.fixed(…​)` a test already has.

The examples on this page wire two classes, one that takes each clock:

```kotlin
package snippets.clock

import java.time.Clock as JClock
import java.time.Instant as JInstant
import kotlin.time.Clock
import kotlin.time.Instant

data class Invoice(val issuedAt: Instant)

/** The application's own class, which dates each invoice it issues by its `kotlin.time.Clock`. */
class Invoices(private val clock: Clock) {
  fun issue(): Invoice = Invoice(issuedAt = clock.now())
}

/** A class, such as a library's, which takes a `java.time.Clock`. */
class TokenSigner(private val clock: JClock) {
  fun expiry(): JInstant = clock.instant().plusSeconds(3600)
}
```

## Wire

The Bootable modules that take a `kotlin.time.Clock`, such as [`boot-periodic`](https://rocketraman.github.io/bootable/bootable/next/background/periodic.html.md), [`boot-leader-k8s`](https://rocketraman.github.io/bootable/bootable/next/background/leader.html.md), [`boot-scheduler-db`](https://rocketraman.github.io/bootable/bootable/next/background/scheduler.html.md), and [`boot-telemetry-otel`](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md), take the bound clock, if there is one, with Kodein, Koin, and Metro; with Inara, pass it to their builders.

**Vanilla:**

Pass the clock to each class that takes one, and `asJavaClock()` of it to each that takes a `java.time.Clock`:

```kotlin
/** The application's components, on [clock]: `Clock.System`, or a test's. */
class Components(clock: Clock = Clock.System) {
  val invoices = Invoices(clock)
  val tokenSigner = TokenSigner(clock.asJavaClock())
}
```

**Kodein:**

Import `clockModule(clock)`, which binds both clocks, `Clock.System` by default:

```kotlin
val invoicesModule by DI.Module {
  bindSingleton { Invoices(instance()) }
  bindSingleton { TokenSigner(instance()) }
}

/** The application's DI, on [clock]: `Clock.System`, or a test's. */
fun appDI(clock: Clock = Clock.System) = DI {
  import(clockModule(clock))
  import(invoicesModule)
}
```

The `java.time.Clock` binding wraps the `kotlin.time.Clock` binding, so a binding of a `kotlin.time.Clock` of your own, with `overrides = true`, replaces both.

**Koin:**

Include `clockModule(clock)`, which defines both clocks, `Clock.System` by default:

```kotlin
val invoicesModule = module {
  single { Invoices(get()) }
  single { TokenSigner(get()) }
}

/** The application's modules, on [clock]: `Clock.System`, or a test's. */
fun appModules(clock: Clock = Clock.System) = listOf(clockModule(clock), invoicesModule)
```

The `java.time.Clock` definition gets the `kotlin.time.Clock` definition, so a definition of a `kotlin.time.Clock` of your own, after this module’s, replaces both.

**Metro:**

`boot-clock-metro` contributes both clocks, `Clock.System`, to the graph:

```kotlin
@DependencyGraph(InvoicesScope::class, additionalScopes = [BootScope::class], bindingContainers = [InvoicesBindings::class])
interface InvoicesGraph {
  val invoices: Invoices
}

@BindingContainer
object InvoicesBindings {
  // boot-clock-metro provides both clocks
  @Provides fun invoices(clock: Clock): Invoices = Invoices(clock)
  @Provides fun tokenSigner(clock: JClock): TokenSigner = TokenSigner(clock)
}
```

The `java.time.Clock` is the graph’s `kotlin.time.Clock`, so a graph with another `kotlin.time.Clock` has it as both.

**Inara:**

Compose `clockModule(clock)`, a `ClockModule` that provides both clocks, `Clock.System` by default, and require the `ClockModule` in the application’s own modules:

```kotlin
@DIModule
interface InvoicesModule : @DIModule.Required ClockModule {
  val invoices: Invoices
  val tokenSigner: TokenSigner
}

val invoicesModule = InvoicesModulePart(
  invoices = Binding.single { Invoices(clock) },
  tokenSigner = Binding.single { TokenSigner(javaClock) },
)

@DIModule(composed = true)
interface ClockAppModule : ClockModule, InvoicesModule

/** The application's module, on [clock]: `Clock.System`, or a test's. */
fun clockAppModule(clock: Clock = Clock.System) = ClockAppModule(
  clockModule = clockModule(clock),
  invoicesModule = invoicesModule,
)
```

Bootable’s Inara modules that need a clock, such as `periodicModule()`, require the `ClockModule` too, so they take their time from the same clock.

## Test

A test gives the application a clock of its own, which then is both clocks.

**Vanilla:**

```kotlin
fun invoicesAreDatedByTheClock() {
  val clock = TestClock()
  val components = Components(clock)
  assert(components.invoices.issue().issuedAt == clock.now())
}
```

**Kodein:**

```kotlin
fun invoicesAreDatedByTheClock() {
  val clock = TestClock()
  val invoices = appDI(clock).direct.instance<Invoices>()
  assert(invoices.issue().issuedAt == clock.now())
}
```

**Koin:**

```kotlin
fun invoicesAreDatedByTheClock() {
  val clock = TestClock()
  val koin = koinApplication { modules(appModules(clock)) }.koin
  try {
    assert(koin.get<Invoices>().issue().issuedAt == clock.now())
  } finally {
    koin.close()
  }
}
```

**Metro:**

A dynamic graph, with a binding container of the test’s own, replaces the graph’s `kotlin.time.Clock`:

```kotlin
/** A test's clock, in place of the graph's. */
@BindingContainer
class TestClockBindings(private val clock: TestClock) {
  @Provides fun clock(): Clock = clock
}

fun invoicesAreDatedByTheClock() {
  val clock = TestClock()
  val graph = createDynamicGraph<InvoicesGraph>(TestClockBindings(clock))
  assert(graph.invoices.issue().issuedAt == clock.now())
}
```

**Inara:**

```kotlin
fun invoicesAreDatedByTheClock() {
  val clock = TestClock()
  assert(clockAppModule(clock).invoices.issue().issuedAt == clock.now())
}
```

## Reference

- [`asJavaClock`](https://rocketraman.github.io/bootable/bootable/next/api/boot-clock/com.github.rocketraman.bootable.clock/as-java-clock.html): a `kotlin.time.Clock` as a `java.time.Clock`
- [`asKotlinClock`](https://rocketraman.github.io/bootable/bootable/next/api/boot-clock/com.github.rocketraman.bootable.clock/as-kotlin-clock.html): a `java.time.Clock` as a `kotlin.time.Clock`
