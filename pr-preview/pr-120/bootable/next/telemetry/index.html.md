# Telemetry (OpenTelemetry)

> The application's OpenTelemetry SDK, for traces and metrics, tied to its lifecycle.

`boot-telemetry-otel` gives the application its [OpenTelemetry](https://opentelemetry.io/) SDK, for traces and metrics. `OpenTelemetryService` builds the SDK when it is constructed, so its `openTelemetry` can be handed to instrumentation right away. On shutdown, it exports the spans and metrics still buffered, then shuts the SDK down, so the last interval of telemetry isn’t lost.

The service is in the `Telemetry` phase, the first, so every other service, the database pool included, starts after it and stops before it, and what they record while stopping is in the final export.

By default the SDK pushes traces and metrics over OTLP, to an endpoint you configure. Without a configured endpoint it does nothing. Metrics can still be exposed to Prometheus-compatible scrapers via the [`boot-telemetry-otel-prometheus`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#prometheus) module. The [exporters and integrations](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md) add Prometheus’s scrape, SigNoz, Google Cloud, and a bridge from Micrometer.

## Modules

`boot-telemetry-otel` is the SDK; each exporter or integration depends on it, so an application that uses one needn’t list the SDK. Every module comes in `-kodein`, `-koin`, and `-metro` flavours too, for those wiring styles.

| Module | What it provides |
| --- | --- |
| **SDK: add it, for export over OTLP, or an exporter or integration, which brings it in** |  |
| `boot-telemetry-otel` | `OpenTelemetryService`: the application’s OpenTelemetry SDK, for traces and metrics, pushed over OTLP |
| **Exporters and integrations: optional** |  |
| [`boot-telemetry-otel-prometheus`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#prometheus) | The metrics, for Prometheus to scrape |
| [`boot-telemetry-otel-signoz`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#signoz) | Traces and metrics to SigNoz, over OTLP |
| [`boot-telemetry-otel-gcp`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#gcp) | Google Cloud’s resource attributes, and export to Cloud Trace and Cloud Monitoring over OTLP |
| [`boot-telemetry-otel-micrometer-bridge`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#micrometer-bridge) | A Micrometer `MeterRegistry`, for libraries that report only to Micrometer |
| **API: comes with the SDK, and with the modules that trace** |  |
| `boot-telemetry-otel-common` | `withSpan`, `withRootSpan`, and `Sample`, over the OpenTelemetry API alone, for code that traces without depending on the SDK |

## Install

For export over OTLP alone, add the SDK, in the flavour of your wiring style:

**Vanilla:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel:3.1.0-SNAPSHOT")
}
```

**Kodein:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-koin:3.1.0-SNAPSHOT")
}
```

**Metro:**

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-metro:3.1.0-SNAPSHOT")
}
```

**Inara:**

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

For metrics that Prometheus scrapes, add [`boot-telemetry-otel-prometheus`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#prometheus) instead, which brings in the SDK.

## Configure

The SDK reads `telemetry.sdk`, including its `otlp`, `traces`, and `metrics` subsections. The other telemetry modules each read a section of their own beside it: [`telemetry.prometheus`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#prometheus), [`telemetry.signoz`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#signoz), [`telemetry.gcp`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#gcp), [`telemetry.ktor`](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md#telemetry) for the server, and [`telemetry.httpClient`](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md#telemetry) for the client. Every setting is optional.

```hocon
telemetry.sdk {
  serviceName = notes                                   (1)
  serviceInstanceId = ${?POD_NAME}                      (2)
  otlp {
    endpoint = "http://otel-collector:4318"             (3)
    headers { authorization = ${?OTLP_AUTHORIZATION} }  (4)
  }
  traces.sampleRatio = 0.1                              (5)
}
```

- (1) Names the application to every backend, as the resource’s `service.name`.
- (2) Tells this instance apart from the service’s others, as `service.instance.id`, across restarts: unset, each start gets a random one. Set it where the platform has a stable name for the instance, such as a pod’s, from the Kubernetes downward API.
- (3) Turns OTLP export on: without an endpoint, nothing is exported over OTLP. It is the receiver’s base URL, such as a collector’s; each signal goes to its own path under it, `/v1/traces` and `/v1/metrics`, over HTTP with protobuf.
- (4) Authenticates to a hosted backend; take the key from the environment. The headers are `Masked`, so the config report hides them.
- (5) Records a fraction of the traces, from 0 to 1, to cut their volume; by default every trace is recorded. A trace started elsewhere keeps the caller’s decision.

`serviceVersion` and `environment` needn’t be set: unset, they are `build.version`, the key of the build info that [the health endpoints](https://rocketraman.github.io/bootable/bootable/next/http/health.html.md#build-info) serve, and `env.environment`, the key [`boot-env`](https://rocketraman.github.io/bootable/bootable/next/environment/index.html.md) reads, so each is set in one place. The others include `resourceAttributes`, `otlp.compression` and `otlp.timeout`, `metrics.interval` (60 seconds) and `metrics.runtime` (the JVM’s runtime metrics, on), `enabled` and `otlp` switches for each signal, `registerGlobal`, and `shutdownTimeout`: see [all the settings](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel/com.github.rocketraman.bootable.telemetry.otel/-telemetry-config/index.html).

### The resource

The resource describes the application to the backend, and is on every span and metric. It starts from the SDK’s default, then `resourceAttributes`, then the typed settings: `serviceName`, `serviceVersion`, `serviceInstanceId`, and `environment`, each winning over what comes before it. A customizer can add to it, as [`boot-telemetry-otel-gcp`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#gcp) does with the platform’s attributes.

### The standard environment variables

OpenTelemetry defines environment variables that platforms and tools set, such as the OpenTelemetry operator for Kubernetes. `telemetryConfig`, which decodes `telemetry.sdk`, falls back to four of them when the corresponding config values are unset: explicit config settings override the standard environment variables.

| Variable | Setting | When both are set |
| --- | --- | --- |
| `OTEL_SERVICE_NAME` | `serviceName` | The setting wins. The variable wins over a `service.name` in `resourceAttributes`, as it does in other SDKs. |
| `OTEL_RESOURCE_ATTRIBUTES` | `resourceAttributes` | Merged by key; the setting wins for a key in both. |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `otlp.endpoint` | The setting wins. |
| `OTEL_EXPORTER_OTLP_HEADERS` | `otlp.headers` | Merged by name; the setting wins for a header in both. |

The variables take their standard `key1=value1,key2=value2` format, and a variable that is empty, or only whitespace, is treated as unset. A setting from any of the config’s sources is explicit, so a `TELEMETRY_SDK_SERVICENAME` environment variable wins over `OTEL_SERVICE_NAME`. No other `OTEL_*` variable is read, such as `OTEL_TRACES_SAMPLER`: set the matching config instead, which a substitution can take from any variable, such as `sampleRatio = ${?TRACES_SAMPLE_RATIO}`.

A `TelemetryConfig` built in code, such as in a test, ignores the variables, so it doesn’t depend on the environment it runs in; `withOtelEnvironment()` applies them to it.

## Wire

The wiring adds `OpenTelemetryService` to the application’s services and hands its `openTelemetry` to whatever records telemetry. It also adds `ServiceStartTracing`, a lifecycle controller that traces the start; see [Trace the start](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md#start-tracing).

**Vanilla:**

```kotlin
  val telemetry = OpenTelemetryService(telemetryConfig(binder))  // from telemetry.sdk and the OTEL_* variables
  val index = NoteIndex(telemetry.openTelemetry)                 // hand its OpenTelemetry to what records telemetry

  Bootable(
    setOf(
      telemetry,
      // ... and the services that use `index`
    ),
    defaultLifecycleControllers() + ServiceStartTracing(telemetry.openTelemetry),  // the start, as one trace
  )
```

**Kodein:**

`openTelemetryModule` binds the `OpenTelemetry`, and opens the sets of customizers and metrics snapshot providers that the other telemetry modules add to, so import it before them:

```kotlin
fun appDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(openTelemetryModule)        // the SDK, from telemetry.sdk, as an OpenTelemetry binding
  import(serviceStartTracingModule)  // the start, as one trace
  // ... and the services that record telemetry, given instance<OpenTelemetry>()
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
```

**Koin:**

`openTelemetryModule()` defines the `OpenTelemetry`, over every customizer and metrics snapshot provider definition, such as those the other telemetry modules define:

```kotlin
fun appKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    openTelemetryModule(),        // the SDK, from telemetry.sdk, as an OpenTelemetry definition
    serviceStartTracingModule(),  // the start, as one trace
    // ... and the services that record telemetry, given get<OpenTelemetry>()
  )
}.koin

fun main() = boot {
  appKoin().get<Bootable>()
}
```

**Metro:**

`boot-telemetry-otel-metro` contributes the `OpenTelemetry` and the service on its own. The start’s tracing isn’t contributed, so that the dependency alone doesn’t change how the application starts; name `ServiceStartTracingBindings` in the graph:

```kotlin
// boot-telemetry-otel-metro contributes the SDK, from telemetry.sdk, as an OpenTelemetry binding; the start's tracing
// is named here, as a binding container
@DependencyGraph(
  TelemetryScope::class,
  additionalScopes = [BootScope::class],
  bindingContainers = [ServiceStartTracingBindings::class],
)
interface TelemetryGraph : BootableGraph

fun main() = boot {
  createGraph<TelemetryGraph>().bootable
}
```

**Inara:**

`openTelemetryModule()` provides the `OpenTelemetry`, and lists the sets of customizers and metrics snapshot providers that the other telemetry modules mark their members as:

```kotlin
@DIModule(composed = true)
interface AppModule : ConfigModule, BootableModule, OpenTelemetryModule, ServiceStartTracingModule {
  // ... and the services that record telemetry, given openTelemetry
}

fun appModule() = AppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),              // the SDK, from telemetry.sdk, as the openTelemetry member
  serviceStartTracingModule = serviceStartTracingModule(),  // the start, as one trace
)

fun main() = boot {
  appModule().bootable
}
```

A `kotlin.time.Clock`, if one is given to `OpenTelemetryService` and `ServiceStartTracing`, or bound with Kodein, Koin, or Metro, or given to the Inara modules' builders, is the time of the spans and measurements, such as a fixed clock in a test; otherwise it is `Clock.System`.

When the OpenTelemetry Java agent is attached, it has already built and registered the process’s SDK: `openTelemetry` is then the agent’s, the config and customizers are ignored, and the agent owns the SDK’s lifecycle.

## Push and pull

With push, the application sends its telemetry: the SDK exports spans as they finish, and metrics every `metrics.interval`, over OTLP to `otlp.endpoint`. With pull, the backend fetches it: a scraper, such as Prometheus, fetches the application’s metrics on its own schedule. Traces are always pushed; metrics can be pushed, pulled with [`boot-telemetry-otel-prometheus`](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#prometheus), or both.

Pull needs no credentials in the application, and tells the backend when a target is down, because the scrape fails. Push works where nothing can reach the application to scrape it, such as short-lived jobs, or Cloud Run, whose instances have no stable address. The application usually pushes to an OpenTelemetry collector beside it, which batches, retries, and holds the backend’s credentials; some backends take OTLP directly, such as [SigNoz](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#signoz) and [Google Cloud](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#gcp).

- With a pulling backend, such as Prometheus, already in place, let it pull the metrics, and push the traces.
- With a collector, or a hosted backend, push both, and leave Prometheus out.
- Locally, nothing needs to be running: without an endpoint nothing is pushed, and the metrics can still be pulled.

## Spans

`withSpan`, `withRootSpan`, and `Sample` are in `boot-telemetry-otel-common`, which depends only on the OpenTelemetry API, not the SDK, so a library can use them, and leave the SDK to the application. `boot-telemetry-otel` brings it in.

`Tracer.withSpan` runs a suspending block in a new span, a child of the current one, such as a traced server’s request span:

```kotlin
  suspend fun rebuild(ids: List<String>) = tracer.withSpan("notes.rebuild") { span ->
    span.setAttribute("notes.count", ids.size.toLong())
    ids.forEach { index(it) }
  }
```

The span stays current across every thread the block suspends and resumes on, so spans and log events within it belong to its trace. It ends when the block does, with an error status and the exception recorded if the block throws, or a status of `cancelled` if the coroutine is cancelled.

A span from `withSpan` is expected to have a parent. Without one, it starts a trace of its own and logs a warning, once for each span name: usually the caller’s trace context didn’t reach it, such as across a new `CoroutineScope` or a callback from a Java library. To start a trace on purpose, such as for a scheduled job or a consumed message, use `withRootSpan`, which can also link the trace to the operation that kicked it off:

```kotlin
  suspend fun compact() = tracer.withRootSpan("notes.compact", sample = Sample.Always) {
    rebuild(emptyList())  // its span is a child of notes.compact
  }
```

`Sample.Always` samples the trace whatever `sampleRatio` is, for a trace too rare or too valuable to lose; the default, `Sample.BySampler`, leaves it to the sampler.

Log events within a span carry its `trace_id` and `span_id`, which [`boot-logging-log4j2`](https://rocketraman.github.io/bootable/bootable/next/logging/index.html.md)'s layouts print, so a backend can link them to their traces. Logs themselves aren’t exported over OpenTelemetry: the SDK has no logger provider, so ship them with the platform’s log collection.

## Trace the start

`ServiceStartTracing` traces the application’s start as one trace, so its total time, and each service’s share of it, can be seen. The root span, `start application`, begins when `boot` was called, so it covers building the application, such as decoding its config, and ends when the application is running. Under it is a span for each stage before the first service starts, `initialize JVM classes for logging and configuration`, `plan start`, and `initialize`, then one for each service’s start, such as `start FlywayMigrationService`. What a service does as it starts is within its span, such as the queries of a migration.

The start’s trace is always sampled, whatever `sampleRatio` is, so a slow or failed start is never missing. A start that fails records the exception, with an error status, on the service’s span and on the root span.

The span is carried in the coroutine context of the service’s `start`, so it is current on whatever thread the start runs on, including in `withContext(Dispatchers.IO)`, and the coroutines the start runs and waits for inherit it.

## Customize the SDK

For an SDK setting the config doesn’t cover, such as a metric view, a span processor, or another sampler, implement `OpenTelemetryCustomizer`. Each of its methods has a default that changes nothing, so implement only what you need:

```kotlin
/** Buckets of its own for the server's request durations, in seconds: a metric view, which the config doesn't cover. */
object RequestDurationBuckets : OpenTelemetryCustomizer {
  override fun customizeMeterProvider(builder: SdkMeterProviderBuilder) {
    builder.registerView(
      InstrumentSelector.builder().setName("http.server.request.duration").build(),
      View.builder()
        .setAggregation(Aggregation.explicitBucketHistogram(listOf(0.01, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5)))
        .build(),
    )
  }
}
```

They are called as the SDK is built, after the typed settings: `customizeResource`, `customizeSampler`, then, for each OTLP exporter, `exportHeaders` and `customizeSpanExporter` or `customizeMetricExporter`, then `customizeTracerProvider` and `customizeMeterProvider`. For a header computed for each export request, such as an access token, use `exportHeaders` rather than the exporter’s `setHeaders`, which would replace every other customizer’s. This is how the exporters and integrations plug in, too.

`excludeUrlPathsFromTraces(paths)` is a customizer that records no trace for a server request to any of the paths, such as a health check, which would otherwise fill the traces with requests nobody looks at.

**Vanilla:**

Pass the customizers to `OpenTelemetryService`:

```kotlin
  OpenTelemetryService(
    telemetryConfig(binder),
    listOf(
      RequestDurationBuckets,
      excludeUrlPathsFromTraces(setOf("/ping")),  // no trace for these requests
    ),
  )
```

**Kodein:**

Add them to the `Set<OpenTelemetryCustomizer>`, in a module imported after `openTelemetryModule`:

```kotlin
  inBindSet<OpenTelemetryCustomizer> {
    add { singleton { RequestDurationBuckets } }
    add { singleton { excludeUrlPathsFromTraces(setOf("/ping")) } }  // no trace for these requests
  }
```

**Koin:**

Define each as an `OpenTelemetryCustomizer`, with a qualifier of its own, for `openTelemetryModule()` to collect:

```kotlin
single<OpenTelemetryCustomizer>(named<RequestDurationBuckets>()) { RequestDurationBuckets }
single<OpenTelemetryCustomizer>(named("ping")) { excludeUrlPathsFromTraces(setOf("/ping")) }  // no trace for these requests
```

**Metro:**

Contribute them into the `Set<OpenTelemetryCustomizer>`:

```kotlin
  @Provides @IntoSet fun requestDurationBuckets(): OpenTelemetryCustomizer = RequestDurationBuckets

  @Provides @IntoSet  // no trace for these requests
  fun excludePing(): OpenTelemetryCustomizer = excludeUrlPathsFromTraces(setOf("/ping"))
```

**Inara:**

Mark them `@OpenTelemetryCustomizers`, in a module that requires `OpenTelemetryModule`:

```kotlin
@DIModule
interface TelemetryCustomizersModule : @DIModule.Required OpenTelemetryModule {
  @OpenTelemetryCustomizers val requestDurationBuckets: OpenTelemetryCustomizer
  @OpenTelemetryCustomizers val pingExclusion: OpenTelemetryCustomizer
}

fun telemetryCustomizersModule() = TelemetryCustomizersModulePart(
  requestDurationBuckets = Binding.value(RequestDurationBuckets),
  pingExclusion = Binding.single { excludeUrlPathsFromTraces(setOf("/ping")) },  // no trace for these requests
)
```

## Telemetry in other functions

Other functions record through the application’s OpenTelemetry, with `-otel` modules of their own, or with tracing built in, each documented with its function:

- Database: a span for each JDBC statement, in [Database](https://rocketraman.github.io/bootable/bootable/next/database/index.html.md#telemetry), and the connection pool’s metrics, in [HikariCP](https://rocketraman.github.io/bootable/bootable/next/database/hikari.html.md#metrics).
- HTTP server: a span and the standard metrics for each request, and the metrics scrape, in [the server](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md#telemetry).
- HTTP client: a client span and the standard metrics for each request, in [the client](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md#telemetry).
- Background work: a trace for each run of a periodic loop, in [periodic work](https://rocketraman.github.io/bootable/bootable/next/background/periodic.html.md#telemetry), and for each db-scheduler execution, in [scheduled tasks](https://rocketraman.github.io/bootable/bootable/next/background/scheduler.html.md#telemetry), built into `boot-periodic` and `boot-scheduler-db`: with Kodein, Koin, or Metro, on whenever the application has an `OpenTelemetry`, and with Inara, through the `OpenTelemetryApiModule` the modules require.

For a library without such a module, hand the application’s `openTelemetry` to the library’s own OpenTelemetry instrumentation, where you build it. For one that reports only to Micrometer, use the [Micrometer bridge](https://rocketraman.github.io/bootable/bootable/next/telemetry/exporters.html.md#micrometer-bridge).

## Example

`examples/server-ktor-db` is a small Ktor server that traces its requests, with a span for each query within them, traces its start, and records its request, pool, and runtime metrics, which it serves for Prometheus at [http://localhost:8001/metrics](http://localhost:8001/metrics). It is wired each of the five ways; from the repository root, run yours:

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

Its metrics are pulled, so they need nothing else running; its traces are pushed, so to see them, point `OTEL_EXPORTER_OTLP_ENDPOINT` at an OpenTelemetry collector or Jaeger. Its README shows how to see both locally, with Grafana or Jaeger in a container.

## Reference

- [`OpenTelemetryService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel/com.github.rocketraman.bootable.telemetry.otel/-open-telemetry-service/index.html): the SDK’s service
- [`TelemetryConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel/com.github.rocketraman.bootable.telemetry.otel/-telemetry-config/index.html): the settings, decoded by [`telemetryConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel/com.github.rocketraman.bootable.telemetry.otel/telemetry-config.html)
- [`withOtelEnvironment`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel/com.github.rocketraman.bootable.telemetry.otel/with-otel-environment.html): the standard environment variables
- [`ServiceStartTracing`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel/com.github.rocketraman.bootable.telemetry.otel/-service-start-tracing/index.html): the start’s trace
- [`withSpan`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel-common/com.github.rocketraman.bootable.telemetry.otel/with-span.html) and [`withRootSpan`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel-common/com.github.rocketraman.bootable.telemetry.otel/with-root-span.html): spans around suspending code
- [`Sample`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel-common/com.github.rocketraman.bootable.telemetry.otel/-sample/index.html): how a root span’s trace is sampled
- [`OpenTelemetryCustomizer`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel/com.github.rocketraman.bootable.telemetry.otel/-open-telemetry-customizer/index.html): for what the config doesn’t cover
- [`excludeUrlPathsFromTraces`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel/com.github.rocketraman.bootable.telemetry.otel/exclude-url-paths-from-traces.html): requests without traces
