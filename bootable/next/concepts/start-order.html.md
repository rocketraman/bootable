# Start order

> How Bootable orders services: phases, milestones within a phase, the tiebreaker, the logged start plan, and custom start orders.

Services start phase by phase, and, within a phase, after the services that provide the milestones they wait for. Services that neither orders are tied, and a tiebreaker orders them. They stop in exactly the reverse order.

Each service says where it starts with its `ordering()`, an `Ordering` of its phase and its milestones. Bootable computes the order, the _start plan_, from the set of services it is given, before anything starts, and logs it. The order is the same whether the services were wired by hand, by Kodein, by Koin, or by Metro, since it comes from the services themselves.

## Phases

Bootable’s default phases, in start order:

| Phase | Holds | Bootable’s modules |
| --- | --- | --- |
| `Telemetry` | What every other service records into, so it can record from its first moment, and what it records while it stops is still exported. | The OpenTelemetry SDK; the [health probes' own listener](https://rocketraman.github.io/bootable/bootable/next/http/health.html.md#listener), so liveness answers while the others start. |
| `Infrastructure` | Connections to the systems the application needs, and the plumbing the rest relies on. | The [database pool](https://rocketraman.github.io/bootable/bootable/next/database/index.html.md), and Exposed’s `Database`; the [HTTP clients](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md); [leader election](https://rocketraman.github.io/bootable/bootable/next/background/leader.html.md). |
| `Initialization` | Work that brings shared state into shape before the application uses it, such as migrations, schema checks, seed data, and cache warm-up. | [Flyway’s migrations](https://rocketraman.github.io/bootable/bootable/next/database/flyway.html.md); Exposed’s [schema verification](https://rocketraman.github.io/bootable/bootable/next/database/exposed.html.md). |
| `Application` | The application’s own services: what the work needs, but not what brings the work in. The default phase. | None: this phase is the application’s. |
| `Serving` | Whatever brings work in: HTTP and gRPC servers, queue consumers, schedulers. It starts last, once what it uses is running, and stops first, so work in flight can finish. | The [Ktor server](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md); [periodic work](https://rocketraman.github.io/bootable/bootable/next/background/periodic.html.md); the [db-scheduler](https://rocketraman.github.io/bootable/bootable/next/background/scheduler.html.md). |

Most of an application’s services declare no phase, and start in `Application`. A service that brings work in declares `Serving`:

```kotlin
class OrderConsumer : AppService {
  // brings work in, so it starts once everything it uses has, and stops first
  override fun ordering() = Ordering(DefaultPhase.Serving)
}
```

To choose a phase, ask, in this order:

1. Does every other service record into it? `Telemetry`. Applications rarely write one.
2. Does it bring work in? `Serving`, whatever the protocol: a timer firing is work arriving, as a request is.
3. Is it a connection, or plumbing, that others use? `Infrastructure`: the application’s own handle on a system, such as its pool, not the system itself.
4. Does it change or check shared state once, before anyone uses it? `Initialization`.
5. Otherwise, `Application`.

A service that is both used by others and brings work in, such as one that consumes messages, is usually two services: the part others use in `Application`, and the consumer in `Serving`.

The `Initialization` phase isn’t a lifecycle controller’s `appInit`, logged as `⇒ Initializing`, which runs before any service starts; see [the start](https://rocketraman.github.io/bootable/bootable/next/concepts/lifecycle.html.md#start).

## Milestones

Within a phase, a milestone orders one service after another. A milestone is a named point that a service reaches once its `start` returns. A service `provides` milestones, and starts `after` the ones it waits for:

```kotlin
object PricingMilestones {
  val RatesLoaded = milestone("pricing.rates.loaded")
}

class ExchangeRates : AppService {
  override suspend fun start() { /* load today's rates */ }
  override fun ordering() = Ordering(provides = setOf(PricingMilestones.RatesLoaded))
}

class PriceCalculator(private val rates: ExchangeRates) : AppService {
  override suspend fun start() { /* compute the prices from the rates */ }
  override fun ordering() = Ordering(after = setOf(PricingMilestones.RatesLoaded))
}
```

A service waits for every provider of a milestone, and a milestone can have several, or none. A provider in an earlier phase has already started, so a milestone matters only between services in the same phase.

A milestone names a role, not a class, so a service orders itself after a role, whichever module fills it. Bootable’s own:

- `DbMilestones.DataSourceReady`, when the connection pool has started, and `DbMilestones.SchemaMigrated`, when the migrations have run: Exposed’s schema verification runs after the migrations without knowing which tool ran them, or whether there is one.
- `HttpClientMilestones.ClientsReady`, when the HTTP clients have started, which leader election waits for.

`Phase` and `Milestone` are interfaces with only a `name`, so an `enum` or an `object` is one with no code; `milestone(name)` makes one that is just a name.

### `after` or `requires`

`after` is the everyday relation: it waits for the providers there are, and is satisfied if there are none. `requires` waits too, but fails the boot if no service provides the milestone:

```kotlin
class ReportService(private val dataSource: DataSource) : AppService {
  // expects the schema to be migrated, by whichever migration tool the application uses
  override fun ordering() = Ordering(requires = setOf(DbMilestones.SchemaMigrated))
}
```

Use `requires` only for a precondition that nothing else enforces. If a service receives what it depends on in its constructor, as `PriceCalculator` does, the wiring already guarantees it exists, and `after` is enough.

## Unordered services

Two services in the same phase, with no milestone between them, are unordered. Bootable may start them in either order, and which it is isn’t part of the contract: if the order matters, a milestone is missing. There is deliberately no priority number to break ties, since a number would be easier to write than the milestone it stands for.

A `StartOrder.Tiebreaker`, given to `Bootable`, decides how ties are broken:

**`ByName`, the default**

By `name`, so the start plan, and the logs, are the same on every run and every machine.

**`Shuffled`**

At random, from a seed that is logged with the plan, so a missing milestone that a fixed order would hide shows up, and `Shuffled(seed)` replays the order.

Set it in config, in `boot.service.order`:

```hocon
boot.service.order {
  tiebreaker {
    _type = Shuffled  (1)
    seed = 42         (2)
  }
}
```

- (1) `ByName` if unset; `BOOT_SERVICE_ORDER_TIEBREAKER=Shuffled` as an environment variable.
- (2) Optional: replays a logged shuffle; `BOOT_SERVICE_ORDER_TIEBREAKER_SEED=42` as an environment variable.

A good approach is to shuffle in `application-local.conf`, for development, and in the tests' config, where a surprise is cheap, and to keep the default in production, so it starts in the same order on every run. Kodein’s `bootableModule`, Koin’s `bootableModule()`, and Metro’s `BootableDefaultBindings` read the section when there is a `ConfigBinder`; wired by hand, pass `tiebreaker = serviceOrderConfig(binder).tiebreaker` to `Bootable`, as [Custom start orders](https://rocketraman.github.io/bootable/bootable/next/concepts/start-order.html.md#custom) shows. A test can check the start plan under many shuffles, without starting anything; see [the start plan](https://rocketraman.github.io/bootable/bootable/next/testing/index.html.md#start-plans).

## Shutdown order

Services stop in the reverse of the start plan, which is what makes the phases useful at shutdown:

1. `Serving` stops first, so no new work comes in, and work in flight finishes while what it uses still runs.
2. `Application` stops next, once nothing calls into it.
3. `Initialization` usually has nothing to stop.
4. `Infrastructure` stops next: the pools close once nothing uses them.
5. `Telemetry` stops last, so its exporters flush what the other phases recorded as they stopped.

Within a phase, a service stops before the providers of the milestones it waited for.

## The start plan

Bootable logs the plan before anything starts, each phase with its services, in order, leaving out empty phases, as for an application with a database and a server:

```kotlin
Start plan: Infrastructure[JdbcDataSourceService, ExposedDatabaseService] Initialization[FlywayMigrationService, SchemaVerificationService] Serving[EntryServer]
```

With a shuffled tiebreaker, it names the seed: `Start plan (ties shuffled, seed 42): …​`.

The same computation is a public function, `startPlan(services, startOrder, tiebreaker)`, which returns the services in the order they would start, without starting them.

### Validation

Bootable computes the plan before any lifecycle controller runs, so a plan that can’t be met fails the boot before anything has started. It lists every problem it finds, such as:

- A service in a phase the start order doesn’t have:

  ```kotlin
  SecretsLoader is in phase Secrets, which is not one of the start order's phases: [Telemetry, Infrastructure, Initialization, Application, Serving]. Pass Bootable a StartOrder that includes Secrets.
  ```
- A required milestone that no service provides:

  ```kotlin
  ReportService requires milestone 'db.schema.migrated', but no service provides it. Add a service that provides it, or use 'after' if it is optional.
  ```
- A milestone whose provider is in a later phase:

  ```kotlin
  PriceCalculator (Application) must start after 'pricing.rates.loaded', but its provider ExchangeRates is in Serving, which starts later
  ```
- A cycle, with its path:

  ```kotlin
  Start-order cycle in phase Application: ExchangeRates (after 'pricing.prices.ready') -> PriceCalculator (after 'pricing.rates.loaded') -> ExchangeRates
  ```
- A start order that repeats a phase, or whose default phase isn’t one of its phases.

## Custom start orders

The default phases are in `DefaultStartOrder`, an implementation of `StartOrder`. An application can give `Bootable` its own `StartOrder`. A `StartOrder` has the `phases`, in start order, the `defaultPhase` of a service that names none, and `orderingOf(service)`, which reads a service’s ordering.

To add a phase, delegate to `DefaultStartOrder`, and add yours to its phases. Here, a phase that fetches the secrets, before any connection opens:

```kotlin
enum class AppPhase : Phase { Secrets }

object AppStartOrder : StartOrder by DefaultStartOrder {
  override val phases = listOf(AppPhase.Secrets) + DefaultStartOrder.phases
}

class SecretsLoader : AppService {
  override fun ordering() = Ordering(AppPhase.Secrets)
}
```

Pass it to `Bootable`:

**Vanilla:**

```kotlin
  Bootable(
    setOf(SecretsLoader(), rates, PriceCalculator(rates), OrderConsumer()),
    startOrder = AppStartOrder,
    tiebreaker = serviceOrderConfig(binder).tiebreaker,  // from boot.service.order
  )
```

**Kodein:**

`bootableModule` passes a `StartOrder` binding, if there is one, to `Bootable`:

```kotlin
  import(bootableModule)
  import(hopliteConfigModule)                     // the tiebreaker, from boot.service.order
  bindSingleton<StartOrder> { AppStartOrder }     // passed to Bootable
  bindAppService { singleton { SecretsLoader() } }
```

**Koin:**

`bootableModule()` passes a `StartOrder` definition, if there is one, to `Bootable`:

```kotlin
bootableModule(),
hopliteConfigModule(),                    // the tiebreaker, from boot.service.order
module {
  single<StartOrder> { AppStartOrder }  // passed to Bootable
  single<AppService>(named<SecretsLoader>()) { SecretsLoader() }
},
```

**Metro:**

`BootableDefaultBindings` passes a `StartOrder` binding, if the graph has one, to `Bootable`:

```kotlin
// boot-config-hoplite-metro's ConfigBinder gives Bootable the tiebreaker, from boot.service.order
@ContributesTo(AppScope::class)
interface StartOrderBindings {
  @Provides fun startOrder(): StartOrder = AppStartOrder  // passed to Bootable

  @Provides @IntoSet @SingleIn(AppScope::class)
  fun secretsLoader(): AppService = SecretsLoader()
}
```

**Inara:**

The start order is a parameter of `bootableModule(…​)`:

```kotlin
@DIModule(composed = true)
interface OrderedAppModule : ConfigModule, BootableModule {
  @AppServices val secretsLoader: SecretsLoader
}

fun orderedAppModule() = OrderedAppModule(
  configModule = hopliteConfigModule(),                         // the tiebreaker, from boot.service.order
  bootableModule = bootableModule(startOrder = AppStartOrder),  // a parameter, passed to Bootable
  secretsLoader = Binding.single { SecretsLoader() },
)
```

Bootable’s own services keep the `DefaultPhase`s they declare, so the list mixes the two. A start order can also replace the phases with its own, mapping each `DefaultPhase` onto one of them in `orderingOf`, or use `orderingOf` to place a service it didn’t write. A phase that a service is in, and the start order lacks, fails the boot, so nothing is silently misplaced.

The [Custom start order](https://rocketraman.github.io/bootable/bootable/next/examples/start-order.html.md) example adds a phase at the other end, after `Serving`, for a service that announces the application is ready once its server listens.

## Reference

- [`Ordering`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-ordering/index.html): a service’s place in the order
- [`DefaultPhase`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-default-phase/index.html): the default phases
- [`Milestone`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-milestone/index.html) and [`milestone`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/milestone.html): milestones
- [`StartOrder`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-start-order/index.html) and [`DefaultStartOrder`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-default-start-order/index.html): the start order
- [`Tiebreaker`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-start-order/-tiebreaker/index.html): how ties are broken
- [`ServiceOrderConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/-service-order-config/index.html): the `boot.service.order` config
- [`startPlan`](https://rocketraman.github.io/bootable/bootable/next/api/boot/com.github.rocketraman.bootable.boot/start-plan.html): the start plan, for tests
- [`DbMilestones`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-jdbc/com.github.rocketraman.bootable.db.jdbc/-db-milestones/index.html) and [`HttpClientMilestones`](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor/com.github.rocketraman.bootable.client.http.ktor/-http-client-milestones/index.html): Bootable’s milestones
