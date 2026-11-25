# Server (Ktor)

> A Ktor server that starts and stops with the application, on the engine of your choice, traced and measured.

`boot-server-http-ktor` runs a Ktor server as one of the application’s services: `KtorService` starts the server when the application starts, and stops it when it stops. It configures Ktor only as far as the server’s address and its shutdown; the plugins and routes are the application’s own.

A `KtorService` is in the `Serving` phase, so it starts once the services it uses have started, and stops first, so requests in flight can finish. It runs in the scope its `start(scope)` gets, and keeps it as `scope`, so a route that finds the server can’t go on fails it, with `scope.launch { throw e }`, which [stops the application](https://rocketraman.github.io/bootable/bootable/next/concepts/lifecycle.html.md#failure), with exit code 1.

## Install

Add an engine’s module, which brings `boot-server-http-ktor` and the engine with it, here CIO. With a DI, add `boot-server-http-ktor` too, for its flavour, which provides `KtorHealth`, and the engine module’s flavour, which provides the engine of the [listeners of their own](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md#listener-engine).

**Vanilla:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel:3.1.0-SNAPSHOT")
}
```

**Kodein:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-kodein:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-koin:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-koin:3.1.0-SNAPSHOT")
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
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-metro:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-metro:3.1.0-SNAPSHOT")
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
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

With telemetry:

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

## The server

A server is a class that extends the engine module’s service class, and writes its Ktor module:

```kotlin
class GreetingServer(
  config: ServerConfig,
  private val health: KtorHealth,
) : CioKtorService("greeting", config) {
  override fun Application.module() {
    installHealth(health)  // the liveness and readiness probes
    routing {
      get("/") { call.respondText("hello") }
    }
  }
}
```

`installHealth` serves the application’s [liveness and readiness probes](https://rocketraman.github.io/bootable/bootable/next/http/health.html.md), and its [build info](https://rocketraman.github.io/bootable/bootable/next/http/health.html.md#build-info); a server without them leaves it out.

The server takes its address as a `HostPort`, which is any config class with a `hostPort`, such as `":8080"` or `"127.0.0.1:8080"`:

```kotlin
data class ServerConfig(override val hostPort: String) : HostPort
```

```hocon
server {
  hostPort = ":8080"  (1)
}
```

- (1) A host, if any, and a port, which defaults to 8080; with no host, the server listens on every interface, `0.0.0.0`.

The section’s name is the application’s choice: it decodes the config class itself.

## Engines

`boot-server-http-ktor` depends on no engine. Choose one, by its module and the service class you extend:

| Module | Extend | Engine |
| --- | --- | --- |
| `boot-server-http-ktor-cio` | `CioKtorService` | Ktor’s CIO: pure Kotlin, on coroutines, with no native transport, and the engine to use for a GraalVM native image. |
| `boot-server-http-ktor-netty` | `NettyKtorService` | Netty. |

Any other engine, such as Jetty, works too: depend on `boot-server-http-ktor` and the engine, and extend `KtorService` with the engine’s factory, as `KtorService<MyEngine, MyEngine.Configuration>("my-server", config, MyEngine)`.

### The engine of the listeners

The listeners that modules start on their own, those of the [health probes](https://rocketraman.github.io/bootable/bootable/next/http/health.html.md#listener) and the [metrics scrape](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md#scrape) when their `hostPort` is set, run on the application’s `KtorServerEngine`. Each engine module’s flavour provides one, for its engine:

- With Kodein, import `ktorServerCioEngineModule`, from `boot-server-http-ktor-cio-kodein`, or `ktorServerNettyEngineModule`, from `boot-server-http-ktor-netty-kodein`.
- With Koin, include `ktorServerCioEngineModule()`, from `boot-server-http-ktor-cio-koin`, or `ktorServerNettyEngineModule()`, from `boot-server-http-ktor-netty-koin`.
- With Metro, `boot-server-http-ktor-cio-metro` or `boot-server-http-ktor-netty-metro` contributes it.
- With Inara, compose `ktorServerCioEngineModule()`, from `boot-server-http-ktor-cio-inara`, or `ktorServerNettyEngineModule()`, from `boot-server-http-ktor-netty-inara`, as the `KtorServerEngineModule`, which the Inara health and telemetry modules require, or `NoKtorServerEngineModule` for none.

Use one engine module’s flavour: two would be two bindings of `KtorServerEngine`. For any other engine, bind your own, such as `KtorServerEngine(Jetty)`. An application that serves its probes and scrape on its own server needs no `KtorServerEngine`; one with a `hostPort` set, and no engine, fails to start, and says so. With vanilla wiring, there is no `KtorServerEngine`: give the engine to each `listener` itself.

### Engine options

Every engine is configured the same way: one connector, at the service’s `HostPort`, a shutdown grace period of 50 ms, and a shutdown timeout of 1 s. To change those, add connectors, or set the engine’s own options, override `configureEngine`, whose receiver is the engine’s own configuration type. It runs after the defaults are set, so what it sets wins:

```kotlin
class NettyGreetingServer(config: ServerConfig) : NettyKtorService("greeting", config) {
  override fun NettyApplicationEngine.Configuration.configureEngine() {
    shutdownTimeout = 5_000            // the longest a stop waits, in ms
    responseWriteTimeoutSeconds = 30   // one of Netty's own options
  }

  override fun Application.module() {
    routing {
      get("/") { call.respondText("hello") }
    }
  }
}
```

## Wire

**Vanilla:**

Construct the server, with the `KtorHealth` it serves, and pass it to `Bootable`, with its `HealthLifecycleController` in place of the default one:

```kotlin
val health = HealthLifecycleController(config = readinessConfig(binder))          // the lifecycle's health, from health.readiness
// served as health.ktor says, with the build info of the build section
val ktorHealth = KtorHealth(health, ktorHealthConfig(binder), buildInfo(binder))
return Bootable(
  setOf(
    GreetingServer(binder.config("server"), ktorHealth),
    ktorHealth.listener(CIO),  // the probes' own listener, if health.ktor.hostPort is set
  ),
  defaultLifecycleControllers().withHealthLifecycleController(health),
)
```

**Kodein:**

Import `ktorHealthModule`, and `ktorServerCioEngineModule` for the engine, bind the server’s config, and add the server to the app services:

```kotlin
val serverModule by DI.Module {
  import(ktorHealthModule)  // KtorHealth, over bootableModule's HealthLifecycleController, from health.ktor
  bindConfig<ServerConfig>("server")
  bindAppService { singleton { new(::GreetingServer) } }
  // the engine of the probes' own listener, which ktorHealthModule adds, if health.ktor.hostPort is set
  import(ktorServerCioEngineModule)
}

fun appDI() = DI {
  import(bootableModule)  // the lifecycle, and its HealthLifecycleController
  import(hopliteConfigModule)
  import(serverModule)
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
```

**Koin:**

Include `ktorHealthModule()`, and `ktorServerCioEngineModule()` for the engine, define the server’s config, and add the server to the app services:

```kotlin
val serverModule = module {
  includes(ktorHealthModule())  // KtorHealth, over bootableModule()'s HealthLifecycleController, from health.ktor
  config<ServerConfig>("server")
  single<AppService>(named<GreetingServer>()) { GreetingServer(get(), get()) }
  // the engine of the probes' own listener, which ktorHealthModule() adds, if health.ktor.hostPort is set
  includes(ktorServerCioEngineModule())
}

fun appKoin() = koinApplication {
  modules(
    bootableModule(),  // the lifecycle, and its HealthLifecycleController
    hopliteConfigModule(),
    serverModule,
  )
}.koin

fun main() = boot {
  appKoin().get<Bootable>()
}
```

**Metro:**

`boot-server-http-ktor-metro` contributes `KtorHealth`, and `boot-server-http-ktor-cio-metro` the engine; contribute the server’s config, and the server into the app services:

```kotlin
@ContributesTo(HttpScope::class)
interface ServerBindings : ConfigBinderProvider {
  @Provides fun serverConfig(): ServerConfig = config("server")

  // KtorHealth is contributed by boot-server-http-ktor-metro
  @Provides @IntoSet fun server(config: ServerConfig, health: KtorHealth): AppService = GreetingServer(config, health)

  // the probes' own listener, if health.ktor.hostPort is set, is contributed too, on the engine that
  // boot-server-http-ktor-cio-metro contributes
}
```

The graph is Bootable’s, with the application’s scope:

```kotlin
// The -metro modules contribute KtorHealth, the KtorClientService, and the telemetry; the application contributes
// its server, its client engine, and what uses them, to HttpScope.
@DependencyGraph(HttpScope::class, additionalScopes = [BootScope::class])
interface HttpGraph : BootableGraph

fun main() = boot {
  createGraph<HttpGraph>().bootable
}
```

**Inara:**

Compose `ktorHealthModule()`, and `ktorServerCioEngineModule()` as the `KtorServerEngineModule` it requires, and give your server’s module the config and the server, marked `@AppServices`:

```kotlin
@DIModule
interface ServerModule : @DIModule.Required KtorHealthModule {
  val serverConfig: ServerConfig
  @AppServices val greetingServer: GreetingServer
}

fun serverModule() = ServerModulePart(
  serverConfig = config("server"),
  greetingServer = Binding.single { GreetingServer(serverConfig, ktorHealth) },
)

@DIModule(composed = true)
interface AppModule : ConfigModule, BootableModule, KtorServerEngineModule, KtorHealthModule, ServerModule

fun appModule() = AppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),  // the lifecycle, and its HealthLifecycleController
  ktorHealthModule = ktorHealthModule(),  // KtorHealth, over that HealthLifecycleController, from health.ktor
  // the engine of the probes' own listener, which ktorHealthModule adds, if health.ktor.hostPort is set
  ktorServerEngineModule = ktorServerCioEngineModule(),
  serverModule = serverModule(),
)

fun main() = boot {
  appModule().bootable
}
```

The probes can have [a listener of their own](https://rocketraman.github.io/bootable/bootable/next/http/health.html.md#listener), on a port of their own, which does nothing unless `health.ktor.hostPort` is set. With vanilla wiring, it is the `listener` among the services; with a DI, the health module adds it.

## Telemetry

`boot-server-http-ktor-otel` adds `KtorTelemetry`, which installs OpenTelemetry’s Ktor server instrumentation in the server. It traces every request, continuing the caller’s trace when the request carries one, and records the standard HTTP server metrics, such as `http.server.request.duration`. For a pulled metrics backend, such as Prometheus, it also serves the scrape. It reports through the application’s OpenTelemetry SDK, from [`boot-telemetry-otel`](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md), which it brings in.

**Vanilla:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-prometheus:3.1.0-SNAPSHOT")
}
```

**Kodein:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-prometheus-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-prometheus-koin:3.1.0-SNAPSHOT")
}
```

**Metro:**

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-prometheus-metro:3.1.0-SNAPSHOT")
}
```

**Inara:**

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-cio-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-prometheus-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

Install it in the server’s module with `installTelemetry`, before the routes:

```kotlin
class TracedGreetingServer(
  config: ServerConfig,
  private val health: KtorHealth,
  private val telemetry: KtorTelemetry,
) : CioKtorService("greeting", config) {
  override fun Application.module() {
    installTelemetry(telemetry)  // trace and measure each request, and serve the scrape
    installHealth(health)
    routing {
      get("/") { call.respondText("hello") }
    }
  }
}
```

The request’s span is current while its route runs, so a span started there, such as with `Tracer.withSpan`, is its child, and log events carry its trace id. So is a call made there through a [traced client](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md#telemetry), which carries the trace on to the server it calls.

### Settings

`KtorTelemetryConfig` reads `telemetry.ktor`; every setting is optional:

```hocon
telemetry.ktor {
  hostPort = ":9090"                       (1)
  capturedRequestHeaders = [X-Request-Id]  (2)
}
```

- (1) Keeps the metrics scrape off a public server, on a listener of its own that only the scraper reaches; see [The metrics scrape](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md#scrape). Unset, the scrape is served on the application’s server.
- (2) Records these request headers on each request’s span, as `http.request.header.<name>`, so a trace can be found by a value the caller sent.

The others are `path`, the scrape’s path, `/metrics` by default, and `capturedResponseHeaders`, recorded as `http.response.header.<name>`: see [all the settings](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-http-ktor-otel/com.github.rocketraman.bootable.server.http.ktor/-ktor-telemetry-config/index.html).

For an instrumentation setting the config doesn’t cover, such as a span’s name or extra attributes, add a `KtorServerTelemetryCustomizer` to its set binding (Kodein `inBindSet`, Koin a definition with a qualifier of its own, Metro `@IntoSet`), or, with vanilla wiring, pass one to `KtorTelemetry`.

### The metrics scrape

With a pulled backend, such as `boot-telemetry-otel-prometheus`, `installTelemetry` serves its scrape at `/metrics` on the application’s server, in the format the request’s `Accept` header prefers. With no pulled backend, there is no scrape route.

A public server would serve the metrics to anyone, so to keep them off it, set `telemetry.ktor.hostPort`. The scrape is then served on a listener of its own, a `MetricsKtorService`, on a port that only the scraper reaches. With Kodein or Koin, `ktorTelemetryModule` adds it to the application’s services, with Metro, `boot-server-http-ktor-otel-metro` does, and with Inara, `ktorTelemetryModule()` does, on the [application’s engine](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md#listener-engine). With vanilla wiring, add `KtorTelemetry.listener(engine)` to the application’s services yourself, such as `telemetry.listener(CIO)`. Without `hostPort`, the listener is a service that does nothing, and needs no engine, so it can be added either way. Like any `KtorService`, the listener is in the `Serving` phase, so a scrape in flight at shutdown is still answered; it isn’t traced.

### Traces without the probes and scrapes

The platform’s probes, and Prometheus’s scrapes, would fill the traces with requests nobody wants to see. The DI flavours keep both out of the traces: the scrape at `telemetry.ktor.path`, and the probes at the paths of `health.ktor`, when they are served on the application’s server. With vanilla wiring, add `excludeScrapeFromTraces()` and `excludeHealthFromTraces()` to `OpenTelemetryService`'s customizers, as below.

### Wire it

**Vanilla:**

Build `OpenTelemetryService` with the customizers that keep the scrapes and the probes out of the traces, hand its `openTelemetry` to `KtorTelemetry`, and add the scrape’s listener:

```kotlin
val prometheus = PrometheusTelemetry(binder.config("telemetry.prometheus"))  // a pulled backend, to scrape
val ktorConfig = binder.config<KtorTelemetryConfig>("telemetry.ktor")
val healthConfig = ktorHealthConfig(binder)
val telemetry = OpenTelemetryService(
  telemetryConfig(binder),
  // neither the scrapes nor the health probes are traced
  listOf(prometheus, ktorConfig.excludeScrapeFromTraces(), healthConfig.excludeHealthFromTraces()),
)
val ktorTelemetry = KtorTelemetry(telemetry.openTelemetry, setOf(prometheus), ktorConfig)
val ktorHealth = KtorHealth(health, healthConfig)

return Bootable(
  setOf(
    telemetry,
    TracedGreetingServer(binder.config("server"), ktorHealth, ktorTelemetry),
    ktorTelemetry.listener(CIO),  // the scrape's own listener, if telemetry.ktor.hostPort is set
    ktorHealth.listener(CIO),
  ),
  defaultLifecycleControllers().withHealthLifecycleController(health),
)
```

**Kodein:**

Import `ktorTelemetryModule` after `openTelemetryModule`, and an engine module’s flavour for the scrape’s listener:

```kotlin
import(openTelemetryModule)        // the application's OpenTelemetry, from boot-telemetry-otel
import(prometheusTelemetryModule)  // a pulled backend, to scrape
import(ktorTelemetryModule)        // KtorTelemetry, from telemetry.ktor, after openTelemetryModule
import(ktorHealthModule)
bindConfig<ServerConfig>("server")
bindAppService { singleton { new(::TracedGreetingServer) } }
// the engine of the scrape's and the probes' own listeners, if their hostPort is set
import(ktorServerCioEngineModule)
```

**Koin:**

Include `ktorTelemetryModule()` with `openTelemetryModule()`, and an engine module’s flavour for the scrape’s listener:

```kotlin
openTelemetryModule(),        // the application's OpenTelemetry, from boot-telemetry-otel
prometheusTelemetryModule(),  // a pulled backend, to scrape
ktorTelemetryModule(),        // KtorTelemetry, from telemetry.ktor, over openTelemetryModule()
ktorHealthModule(),
module {
  config<ServerConfig>("server")
  single<AppService>(named<TracedGreetingServer>()) { TracedGreetingServer(get(), get(), get()) }
},
// the engine of the scrape's and the probes' own listeners, if their hostPort is set
ktorServerCioEngineModule(),
```

**Metro:**

`boot-server-http-ktor-otel-metro` contributes `KtorTelemetry`, and keeps the scrapes and the probes out of the traces; give it to the server; it adds the scrape’s listener too:

```kotlin
// KtorTelemetry is contributed by boot-server-http-ktor-otel-metro
@Provides @IntoSet
fun server(config: ServerConfig, health: KtorHealth, telemetry: KtorTelemetry): AppService =
  TracedGreetingServer(config, health, telemetry)

// the scrape's and the probes' own listeners, if their hostPort is set, are contributed too, on the engine that
// boot-server-http-ktor-cio-metro contributes
```

**Inara:**

Compose `ktorTelemetryModule()`, which requires `openTelemetryModule()`, and an engine module, which it and `ktorHealthModule()` require, for the listeners:

```kotlin
@DIModule(composed = true)
interface TracedServerAppModule :
  ConfigModule, BootableModule, OpenTelemetryModule, PrometheusTelemetryModule,
  KtorServerEngineModule, KtorTelemetryModule, KtorHealthModule {
  val serverConfig: ServerConfig
  @AppServices val tracedServer: TracedGreetingServer
}

fun tracedServerAppModule() = TracedServerAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),              // the application's OpenTelemetry, from boot-telemetry-otel
  prometheusTelemetryModule = prometheusTelemetryModule(),  // a pulled backend, to scrape
  ktorTelemetryModule = ktorTelemetryModule(),              // KtorTelemetry, from telemetry.ktor
  ktorHealthModule = ktorHealthModule(),
  // the engine of the scrape's and the probes' own listeners, if their hostPort is set
  ktorServerEngineModule = ktorServerCioEngineModule(),
  serverConfig = config("server"),
  tracedServer = Binding.single { TracedGreetingServer(serverConfig, ktorHealth, ktorTelemetry) },
)
```

## Example

`examples/server-ktor-db` is a server on CIO, traced and measured, which serves its probes and the scrape, in each of the five wiring styles; see [Web / HTTP](https://rocketraman.github.io/bootable/bootable/next/http/index.html.md). `examples/server-ktor` is a server on Netty, wired by hand.

## Reference

- [`KtorService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-http-ktor/com.github.rocketraman.bootable.server.http.ktor/-ktor-service/index.html): the server’s base class
- [`CioKtorService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-http-ktor-cio/com.github.rocketraman.bootable.server.http.ktor/-cio-ktor-service/index.html): on CIO
- [`NettyKtorService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-http-ktor-netty/com.github.rocketraman.bootable.server.http.ktor/-netty-ktor-service/index.html): on Netty
- [`KtorServerEngine`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-http-ktor/com.github.rocketraman.bootable.server.http.ktor/-ktor-server-engine/index.html): the engine of the listeners of their own
- [`KtorTelemetry`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-http-ktor-otel/com.github.rocketraman.bootable.server.http.ktor/-ktor-telemetry/index.html): the telemetry
- [`KtorTelemetryConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-http-ktor-otel/com.github.rocketraman.bootable.server.http.ktor/-ktor-telemetry-config/index.html): its settings
- [`MetricsKtorService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-http-ktor-otel/com.github.rocketraman.bootable.server.http.ktor/-metrics-ktor-service/index.html): the scrape’s own listener
- [`KtorServerTelemetryCustomizer`](https://rocketraman.github.io/bootable/bootable/next/api/boot-server-http-ktor-otel/com.github.rocketraman.bootable.server.http.ktor/-ktor-server-telemetry-customizer/index.html): for the other settings
