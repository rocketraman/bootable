# Exporters and integrations

> Prometheus's scrape, SigNoz, Google Cloud, and a bridge from Micrometer.

Each of these modules plugs into the application’s [OpenTelemetry SDK](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md) as an `OpenTelemetryCustomizer`, and reads its own subsection of `telemetry`. Each depends on `boot-telemetry-otel`, so an application lists only the ones it uses. They can be combined, such as Prometheus’s scrape for the metrics alongside OTLP export of the traces to SigNoz.

## Prometheus

`boot-telemetry-otel-prometheus` keeps the application’s metrics for Prometheus, or another scraper, to pull; see [push and pull](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md#push-and-pull). `PrometheusTelemetry` registers a metric reader with the SDK, and renders each scrape from it, as a `MetricsSnapshotProvider`, for an HTTP integration to serve.

**Vanilla:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-prometheus:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel:3.1.0-SNAPSHOT")
}
```

**Kodein:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-prometheus-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-prometheus-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-koin:3.1.0-SNAPSHOT")
}
```

**Metro:**

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-prometheus-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-metro:3.1.0-SNAPSHOT")
}
```

**Inara:**

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-prometheus-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor-otel-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

A scrape is answered in the OpenMetrics text format when the scraper accepts it, as Prometheus does, and otherwise in the Prometheus text format. Exemplars, in the OpenMetrics format, carry the trace and span ids of the measurement they sample, so a metric links to a trace.

### Settings

`PrometheusTelemetryConfig` reads `telemetry.prometheus`; every setting is optional, and keeps the OpenTelemetry Prometheus exporter’s default when unset:

```hocon
telemetry.prometheus {
  resourceAttributesAsLabels = [service.name, deployment.environment.name]  (1)
}
```

- (1) Lets queries select series by the application’s own name and environment, copied onto every series as labels; none are by default. Prometheus otherwise identifies the target by its own `job` and `instance` labels.

The others are `targetInfo`, whether the resource is exported as the `target_info` metric, for joining against in queries, and `otelScopeLabels`, whether each series is labelled with the instrumentation scope that recorded it: see [all the settings](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel-prometheus/com.github.rocketraman.bootable.telemetry.otel/-prometheus-telemetry-config/index.html).

With the metrics pulled, `telemetry.sdk.metrics.otlp = false` keeps them off OTLP while the traces are still pushed.

### Serve the scrape

`KtorTelemetry`, from `boot-server-http-ktor-otel`, serves the scrape at `/metrics` on the application’s server, once the server installs it:

```kotlin
    installTelemetry(telemetry)  // traces each request, and serves the scrape at telemetry.ktor.path
```

With `telemetry.ktor.hostPort` set, such as `":9090"`, the scrape is served on a [listener of its own](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md#scrape) instead, which keeps it off a public server: with vanilla wiring, add `KtorTelemetry`'s `listener` to the services either way, since without `hostPort` it does nothing; with a DI, the module adds it, on the engine an engine module’s flavour provides. Scrapes of the application’s server aren’t traced. The server’s other settings are on [the server’s page](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md#telemetry).

**Vanilla:**

Give `PrometheusTelemetry` to `OpenTelemetryService`, as a customizer, and to `KtorTelemetry`, as the scrape to serve:

```kotlin
  val prometheus = PrometheusTelemetry(binder.config("telemetry.prometheus"))
  val ktorConfig = binder.config<KtorTelemetryConfig>("telemetry.ktor")
  val telemetry = OpenTelemetryService(
    telemetryConfig(binder),
    listOf(prometheus, ktorConfig.excludeScrapeFromTraces()),  // registers its reader; no traces of the scrapes
  )
  val ktorTelemetry = KtorTelemetry(telemetry.openTelemetry, setOf(prometheus), ktorConfig)

  return Bootable(
    setOf(
      telemetry,
      StatusServer(binder.config("server"), ktorTelemetry),  // serves the scrape at /metrics
      ktorTelemetry.listener(CIO),                           // or on a port of its own, with telemetry.ktor.hostPort
    ),
  )
```

**Kodein:**

Import `prometheusTelemetryModule` and `ktorTelemetryModule` after `openTelemetryModule`, and an engine module’s flavour for the scrape’s listener:

```kotlin
  import(openTelemetryModule)
  import(prometheusTelemetryModule)  // registers its reader, and provides the scrape
  import(ktorTelemetryModule)        // KtorTelemetry, which serves it; no traces of the scrapes

  bindConfig<StatusServerConfig>("server")
  bindAppService { singleton { new(::StatusServer) } }                   // serves the scrape at /metrics
  import(ktorServerCioEngineModule)  // or on a port of its own, telemetry.ktor.hostPort, on this engine
```

**Koin:**

Include `prometheusTelemetryModule()` and `ktorTelemetryModule()` with `openTelemetryModule()`, and an engine module’s flavour for the scrape’s listener:

```kotlin
openTelemetryModule(),
prometheusTelemetryModule(),  // registers its reader, and provides the scrape
ktorTelemetryModule(),        // KtorTelemetry, which serves it; no traces of the scrapes
module {
  config<StatusServerConfig>("server")
  single<AppService>(named<StatusServer>()) { StatusServer(get(), get()) }  // serves the scrape at /metrics
},
ktorServerCioEngineModule(),  // or on a port of its own, telemetry.ktor.hostPort, on this engine
```

**Metro:**

The `-metro` modules contribute the reader, the scrape, `KtorTelemetry`, and the scrape’s listener; provide the server:

```kotlin
  // boot-telemetry-otel-prometheus-metro and boot-server-http-ktor-otel-metro contribute the reader, the scrape, and
  // the KtorTelemetry that serves it
  @Provides @IntoSet
  fun statusServer(telemetry: KtorTelemetry): AppService = StatusServer(config("server"), telemetry)  // at /metrics

  // or on a port of its own, telemetry.ktor.hostPort, on the engine boot-server-http-ktor-cio-metro contributes
```

**Inara:**

Compose `prometheusTelemetryModule()` and `ktorTelemetryModule()`, which require `openTelemetryModule()`, and an engine module for the scrape’s listener:

```kotlin
@DIModule(composed = true)
interface PrometheusAppModule :
  ConfigModule, BootableModule, OpenTelemetryModule, PrometheusTelemetryModule,
  KtorServerEngineModule, KtorTelemetryModule {
  val statusServerConfig: StatusServerConfig
  @AppServices val statusServer: StatusServer  // serves the scrape at /metrics
}

fun prometheusAppModule() = PrometheusAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),
  prometheusTelemetryModule = prometheusTelemetryModule(),  // registers its reader, and provides the scrape
  ktorTelemetryModule = ktorTelemetryModule(),              // KtorTelemetry, which serves it; no traces of the scrapes
  // or on a port of its own, telemetry.ktor.hostPort, with the listener ktorTelemetryModule adds, on this engine
  ktorServerEngineModule = ktorServerCioEngineModule(),
  statusServerConfig = config("server"),
  statusServer = Binding.single { StatusServer(statusServerConfig, ktorTelemetry) },
)
```

`KtorTelemetry` serves one scrape, so an application has at most one `MetricsSnapshotProvider`.

## SigNoz

[SigNoz](https://signoz.io/) ingests OTLP itself: SigNoz Cloud at `[https://ingest.<region>.signoz.cloud:443](https://ingest.<region>.signoz.cloud:443)`, in its `us`, `eu`, or `in` region, or a self-hosted SigNoz’s collector, on port 4318. Point `telemetry.sdk.otlp.endpoint` there, and `boot-telemetry-otel-signoz`'s `SigNozTelemetry` does the rest:

- It sends the ingestion key, as the `signoz-ingestion-key` header, on export to SigNoz Cloud.
- It sets the resource’s `deployment.environment` too, from `deployment.environment.name`, since SigNoz’s services and environment filters read the older attribute.
- It exports counters and histograms with delta temporality, if you choose it.

**Vanilla:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-signoz:3.1.0-SNAPSHOT")
}
```

**Kodein:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-signoz-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-signoz-koin:3.1.0-SNAPSHOT")
}
```

**Metro:**

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-signoz-metro:3.1.0-SNAPSHOT")
}
```

**Inara:**

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-signoz-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

### Settings

`SigNozTelemetryConfig` reads `telemetry.signoz`; every setting is optional:

```hocon
telemetry {
  sdk.otlp.endpoint = "https://ingest.us.signoz.cloud:443"  (1)
  signoz {
    ingestionKey = ${?SIGNOZ_INGESTION_KEY}                 (2)
    temporality = Delta                                     (3)
  }
}
```

- (1) Sends the telemetry to SigNoz Cloud, or to a self-hosted SigNoz’s collector, such as `[http://signoz-otel-collector:4318](http://signoz-otel-collector:4318)`.
- (2) Authenticates to SigNoz Cloud, with the ingestion key from Settings > Ingestion; take it from a secret. A self-hosted SigNoz needs none. Export to SigNoz Cloud without a key fails when the SDK is built, so the application stops at boot rather than failing every export. The key is sent only to a `signoz.cloud` endpoint, so a key set for every environment stays off a local collector.
- (3) Cuts the samples SigNoz stores, as SigNoz suggests, by exporting only what changed since the last export; `Cumulative` is the default. A delta export that fails is lost for good, where a cumulative one is made up by the next. Up-down counters and gauges are always cumulative.

The others are `authenticate`, to send the key whatever the endpoint, or never, and `legacyEnvironmentAttribute`, to stop setting `deployment.environment`: see [all the settings](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel-signoz/com.github.rocketraman.bootable.telemetry.otel/-sig-noz-telemetry-config/index.html).

### Wire

**Vanilla:**

```kotlin
  OpenTelemetryService(
    telemetryConfig(binder),
    listOf(SigNozTelemetry(binder.config("telemetry.signoz"))),
  )
```

**Kodein:**

Import `sigNozTelemetryModule` after `openTelemetryModule`:

```kotlin
  import(openTelemetryModule)
  import(sigNozTelemetryModule)  // from telemetry.signoz
```

**Koin:**

Include `sigNozTelemetryModule()` with `openTelemetryModule()`:

```kotlin
openTelemetryModule(),
sigNozTelemetryModule(),  // from telemetry.signoz
```

**Metro:**

`boot-telemetry-otel-signoz-metro` contributes `SigNozTelemetry` to the customizers on its own; there is nothing to wire.

**Inara:**

Compose `sigNozTelemetryModule()`, which requires `openTelemetryModule()`:

```kotlin
@DIModule(composed = true)
interface SigNozAppModule : ConfigModule, BootableModule, OpenTelemetryModule, SigNozTelemetryModule

fun sigNozAppModule() = SigNozAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),
  sigNozTelemetryModule = sigNozTelemetryModule(),  // from telemetry.signoz
)
```

## Google Cloud

`boot-telemetry-otel-gcp`'s `GcpTelemetry` connects the application’s telemetry to Google Cloud, with no collector:

- It adds the GCP platform’s attributes to the resource, with OpenTelemetry’s GCP resource detector, such as the Cloud Run service and revision, or the GKE cluster, namespace, and pod. Off GCP, nothing is added, and the application’s own attributes win over what is detected.
- It authenticates OTLP export to Google’s OTLP endpoint, `[https://telemetry.googleapis.com](https://telemetry.googleapis.com)`, which writes traces to Cloud Trace and metrics to Cloud Monitoring, with the application default credentials: a fresh access token on each request, and the quota project as `x-goog-user-project`.

**Vanilla:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-gcp:3.1.0-SNAPSHOT")
}
```

**Kodein:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-gcp-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-gcp-koin:3.1.0-SNAPSHOT")
}
```

**Metro:**

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-gcp-metro:3.1.0-SNAPSHOT")
}
```

**Inara:**

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-gcp-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

The service account needs the roles to write both, `roles/cloudtrace.agent` and `roles/monitoring.metricWriter`, and the project needs the Telemetry API enabled.

### Settings

`GcpTelemetryConfig` reads `telemetry.gcp`; every setting is optional:

```hocon
telemetry {
  sdk.otlp.endpoint = "https://telemetry.googleapis.com"  (1)
  gcp.projectId = my-project                              (2)
}
```

- (1) Sends traces to Cloud Trace and metrics to Cloud Monitoring, through Google’s OTLP endpoint.
- (2) Bills export to, and checks its quota against, another project than the default: the credentials' quota project, then the project the application runs in.

Export to a `googleapis.com` endpoint is authenticated, and export to a collector isn’t. The credentials are loaded when the SDK is built, only if export is authenticated, so missing credentials stop the application at boot. The others are `authenticate`, to authenticate export whatever the endpoint, or never, and `detectResource`, to leave out the platform’s resource attributes: see [all the settings](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel-gcp/com.github.rocketraman.bootable.telemetry.otel/-gcp-telemetry-config/index.html).

### Wire

**Vanilla:**

```kotlin
  OpenTelemetryService(
    telemetryConfig(binder),
    listOf(GcpTelemetry(binder.config("telemetry.gcp"))),
  )
```

**Kodein:**

Import `gcpTelemetryModule` after `openTelemetryModule`:

```kotlin
  import(openTelemetryModule)
  import(gcpTelemetryModule)  // from telemetry.gcp
```

**Koin:**

Include `gcpTelemetryModule()` with `openTelemetryModule()`:

```kotlin
openTelemetryModule(),
gcpTelemetryModule(),  // from telemetry.gcp
```

**Metro:**

`boot-telemetry-otel-gcp-metro` contributes `GcpTelemetry` to the customizers on its own; there is nothing to wire.

**Inara:**

Compose `gcpTelemetryModule()`, which requires `openTelemetryModule()`:

```kotlin
@DIModule(composed = true)
interface GcpAppModule : ConfigModule, BootableModule, OpenTelemetryModule, GcpTelemetryModule

fun gcpAppModule() = GcpAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),
  gcpTelemetryModule = gcpTelemetryModule(),  // from telemetry.gcp
)
```

## Micrometer bridge

`boot-telemetry-otel-micrometer-bridge` is for libraries that report only to Micrometer, such as the Temporal SDK. `openTelemetryMeterRegistry(openTelemetry)` is a Micrometer `MeterRegistry` that records into the application’s OpenTelemetry, so the libraries' meters are exported with the rest of its metrics, with its resource, by whichever exporters the SDK has.

**Vanilla:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-micrometer-bridge:3.1.0-SNAPSHOT")
}
```

**Kodein:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-micrometer-bridge-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-micrometer-bridge-koin:3.1.0-SNAPSHOT")
}
```

**Metro:**

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-micrometer-bridge-metro:3.1.0-SNAPSHOT")
}
```

**Inara:**

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-micrometer-bridge-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

It has no settings. The meters keep their Micrometer names and tags; there are no common tags to set, since the resource describes the application. The registry needs no closing: the SDK exports the meters' last values when `OpenTelemetryService` shuts it down.

**Vanilla:**

```kotlin
  val registry: MeterRegistry = openTelemetryMeterRegistry(telemetry.openTelemetry)
  // e.g. a library's Micrometer binder: the executor's metrics, exported with the rest
  val executor = ExecutorServiceMetrics.monitor(registry, Executors.newFixedThreadPool(4), "work")
```

**Kodein:**

`micrometerBridgeModule` binds the `MeterRegistry`:

```kotlin
  import(openTelemetryModule)
  import(micrometerBridgeModule)  // a MeterRegistry binding, over the application's OpenTelemetry

  // e.g. a library's Micrometer binder: the executor's metrics, exported with the rest
  bindSingleton<ExecutorService> { ExecutorServiceMetrics.monitor(instance(), Executors.newFixedThreadPool(4), "work") }
```

**Koin:**

`micrometerBridgeModule()` defines the `MeterRegistry`:

```kotlin
openTelemetryModule(),
micrometerBridgeModule(),  // a MeterRegistry definition, over the application's OpenTelemetry
module {
  // e.g. a library's Micrometer binder: the executor's metrics, exported with the rest
  single<ExecutorService> { ExecutorServiceMetrics.monitor(get(), Executors.newFixedThreadPool(4), "work") }
},
```

**Metro:**

`boot-telemetry-otel-micrometer-bridge-metro` contributes the `MeterRegistry`:

```kotlin
  // boot-telemetry-otel-micrometer-bridge-metro contributes the MeterRegistry; e.g. a library's Micrometer binder, the
  // executor's metrics, exported with the rest
  @Provides @SingleIn(TelemetryScope::class)
  fun workExecutor(registry: MeterRegistry): ExecutorService =
    ExecutorServiceMetrics.monitor(registry, Executors.newFixedThreadPool(4), "work")
```

**Inara:**

`micrometerBridgeModule()` provides the `MeterRegistry`:

```kotlin
@DIModule(composed = true)
interface MicrometerBridgeAppModule : ConfigModule, BootableModule, OpenTelemetryModule, MicrometerBridgeModule {
  val workExecutor: ExecutorService
}

fun micrometerBridgeAppModule() = MicrometerBridgeAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),
  micrometerBridgeModule = micrometerBridgeModule(),  // a meterRegistry member, over the application's OpenTelemetry
  // e.g. a library's Micrometer binder: the executor's metrics, exported with the rest
  workExecutor = Binding.single {
    ExecutorServiceMetrics.monitor(meterRegistry, Executors.newFixedThreadPool(4), "work")
  },
)
```

## Reference

- [`PrometheusTelemetry`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel-prometheus/com.github.rocketraman.bootable.telemetry.otel/-prometheus-telemetry/index.html) and [`PrometheusTelemetryConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel-prometheus/com.github.rocketraman.bootable.telemetry.otel/-prometheus-telemetry-config/index.html): Prometheus’s scrape
- [`MetricsSnapshotProvider`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel/com.github.rocketraman.bootable.telemetry.otel/-metrics-snapshot-provider/index.html): a scrape, for an HTTP integration to serve
- [`SigNozTelemetry`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel-signoz/com.github.rocketraman.bootable.telemetry.otel/-sig-noz-telemetry/index.html) and [`SigNozTelemetryConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel-signoz/com.github.rocketraman.bootable.telemetry.otel/-sig-noz-telemetry-config/index.html): SigNoz
- [`GcpTelemetry`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel-gcp/com.github.rocketraman.bootable.telemetry.otel/-gcp-telemetry/index.html) and [`GcpTelemetryConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel-gcp/com.github.rocketraman.bootable.telemetry.otel/-gcp-telemetry-config/index.html): Google Cloud
- [`openTelemetryMeterRegistry`](https://rocketraman.github.io/bootable/bootable/next/api/boot-telemetry-otel-micrometer-bridge/com.github.rocketraman.bootable.telemetry.otel/open-telemetry-meter-registry.html): the Micrometer bridge
