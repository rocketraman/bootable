# Client (Ktor)

> The application's Ktor HttpClients, configured from http.client, and closed when the application stops.

`boot-client-http-ktor` gives the application its Ktor `HttpClient`s, tied to its lifecycle. `KtorClientService` builds them when it is constructed, so they can be handed to other classes right away, and a bad setting, such as an unreadable key store, fails the boot. When the application stops, it closes them, and their engines.

They are plain Ktor clients: the typed settings are applied through Ktor’s own plugins, and nothing is wrapped.

The service is in the `Infrastructure` phase, so the services that make calls, in later phases, stop before the clients close, and a call in flight isn’t cut off. It reaches the `HttpClientMilestones.ClientsReady` milestone, so something in the same phase that uses the clients can start after it, with `Ordering(after = setOf(HttpClientMilestones.ClientsReady))`, and stop before them.

## Install

Add an engine’s module, which brings `boot-client-http-ktor` and the engine with it, here CIO. With a DI, add `boot-client-http-ktor` too, for its flavour, which provides the service.

**Vanilla:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-otel:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel:3.1.0-SNAPSHOT")
}
```

**Kodein:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-otel-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

Without telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-otel-koin:3.1.0-SNAPSHOT")
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
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
}
```

With telemetry:

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-otel-metro:3.1.0-SNAPSHOT")
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
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

With telemetry:

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-otel-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

## Clients

There is a default client, `clients.default`, and any number of named ones, such as `clients["github"]`. Each is its own `HttpClient`, with its own connection pool and settings. `clients["name"]` fails for a client that isn’t configured; `clients.getOrDefault(name)` gives the default client instead, for, say, a client per partner, where only some need settings of their own.

Hand a client to whatever makes calls:

```kotlin
/** Uses the `github` client, whose base URL and headers come from `http.client.named.github`. */
class GitHubRepos(private val github: HttpClient) {
  suspend fun repo(owner: String, name: String): String = github.get("repos/$owner/$name").bodyAsText()
}
```

## Configure

`KtorClientsConfig` reads `http.client`: the `default` client, and the `named` ones. Every setting is optional.

```hocon
http.client {
  default {
    connectTimeout = 5s       (1)
    requestTimeout = 30s      (2)
    retry { maxRetries = 3 }  (3)
  }
  named {
    github {
      baseUrl = "https://api.github.com/"                   (4)
      headers { Authorization = ${?GITHUB_AUTHORIZATION} }  (5)
    }
  }
}
```

- (1) Fails a call to a host that can’t be reached quickly, rather than after the engine’s own limit.
- (2) Bounds how long a whole call may take, so a stalled server doesn’t hold up the caller; unset, the engine’s limit applies, which differs between engines (see [Engines](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md#engines)).
- (3) Rides out transient failures; see [Retries](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md#retries).
- (4) Lets calls use relative URLs, through Ktor’s `DefaultRequest`; end it with a `/` to keep its last path segment.
- (5) Authenticates every request that doesn’t set its own; take the token from the environment. Headers are `Masked`, so the config report hides them.

The others include `socketTimeout`, and `tls`, for a client certificate and the certificates to trust (see [TLS](https://rocketraman.github.io/bootable/bootable/next/http/client.html.md#tls)): see [all the settings](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor/com.github.rocketraman.bootable.client.http.ktor/-ktor-client-config/index.html).

A named client takes every setting it leaves unset from the default client. Its `headers` are added to the default client’s, replacing any of the same name, and it takes `retry` and `tls` whole: an empty `tls {}` turns off a client certificate the default client has. A named client can’t be called `default`.

### Retries

`retry`, through Ktor’s `HttpRequestRetry`, retries only transient failures: a 5xx, 408, or 429 response, a timeout, or a failed connection. Any other 4xx is permanent, since the same request would fail the same way, and so is a failed TLS handshake. Unset, a request is never retried.

```hocon
retry {
  maxRetries = 5                                  (1)
  methods = [GET, HEAD, OPTIONS, PUT, DELETE, POST]  (2)
}
```

- (1) Sets how many times a call is retried after its first attempt, 3 by default; 0 turns retries off.
- (2) Retries a `POST` too, for an API where repeating one is safe, such as with an idempotency key. By default only the idempotent methods are retried, so a request that may have changed something isn’t sent twice.

The delay doubles from `initialDelay`, 500 milliseconds, up to `maxDelay`, 30 seconds, with up to `jitter`, 500 milliseconds, added at random, so clients that failed together don’t retry together. When a response has a `Retry-After` header, the client waits at least as long as it asks, even beyond `maxDelay`, unless `respectRetryAfter` is false. Only a `Retry-After` in seconds counts; one given as a date is ignored. [All the settings](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor/com.github.rocketraman.bootable.client.http.ktor/-ktor-client-retry-config/index.html) are optional once `retry` is set. `HttpStatusCode.isTransient()` and `Throwable.isTransient()` give the same answer to code that decides for itself.

### TLS

`tls` holds a client certificate to present, for mutual TLS, and the certificates to trust:

```hocon
tls {
  keyStore {                                  (1)
    path = /etc/partner/client.p12
    password = ${?PARTNER_KEYSTORE_PASSWORD}  (2)
  }
  trustStore {                                (3)
    path = /etc/partner/ca.crt
    type = PEM
  }
}
```

- (1) Presents a client certificate, for a server that requires mutual TLS: the key store holds its key and chain. Unset, the client presents none.
- (2) Opens the key store; take it from a secret.
- (3) Trusts a server whose certificate the platform’s certificate authorities don’t vouch for, such as one from a private CA. Unset, only the platform’s are trusted.

Each store is given inline, as `base64`, so it can come from an environment variable or a secret, or as a file, at `path`, with its `password`, and its `type`: `PKCS12`, the default, or `JKS`. A trust store can also be `PEM`: one or more certificates, such as a CA bundle, or a platform’s mounted `ca.crt`. When the key store holds more than one key entry, `keyAlias` picks the one to present, and `keyPassword` is its password, when it differs from the key store’s; see [all the settings](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor/com.github.rocketraman.bootable.client.http.ktor/-ktor-client-tls-config/index.html). The certificate is presented whatever certificate authorities the server asks for, so a self-signed one works too; the server decides whether to accept it.

## Engines

`boot-client-http-ktor` depends on no engine. Choose one, by its module, and give its `KtorClientEngine` to the service:

| Module | Engine | Notes |
| --- | --- | --- |
| `boot-client-http-ktor-cio` | `CioClientEngine` | Ktor’s CIO: pure Kotlin, on coroutines, and the engine that works best in a GraalVM native image. Its own TLS supports TLS 1.2, but not 1.3 ([KTOR-6737](https://youtrack.jetbrains.com/issue/KTOR-6737)). Unless the client sets a `requestTimeout`, CIO’s own 15 seconds applies. |
| `boot-client-http-ktor-java` | `JavaClientEngine` | The JDK’s `java.net.http.HttpClient`, with the JDK’s TLS, including TLS 1.3, and HTTP/2, and no dependency beyond Ktor’s. It works in a native image too. Unless the client sets a `requestTimeout`, a request has no time limit. |

Each applies the client’s TLS material in its engine’s terms, and takes a lambda that sets the engine’s own options:

```kotlin
val http11 = JavaClientEngine { protocolVersion = java.net.http.HttpClient.Version.HTTP_1_1 }
```

For another engine, such as Apache5 or OkHttp, write a `KtorClientEngine`: a few lines that create the engine from the client’s `KtorClientSpec`, applying its `tls.sslContext`. The engine is also the seam for tests: one that returns Ktor’s `MockEngine` answers every client’s requests, with no other change to the wiring.

## Wire

**Vanilla:**

Construct the service with the engine, hand its clients to whatever makes calls, and pass it to `Bootable`:

```kotlin
val clients = KtorClientService(binder.config("http.client"), CioClientEngine())
val repos = GitHubRepos(clients["github"])  // a named client; clients.default is the default one

return Bootable(
  setOf(
    clients,  // closes the clients when the application stops
    // ... and the services that use `repos`
  ),
)
```

**Kodein:**

Import `ktorClientModule`, which binds the service, adds it to the app services, and binds the default `HttpClient`, and bind the engine:

```kotlin
val clientModule by DI.Module {
  import(ktorClientModule)  // KtorClientService, from http.client, and the default HttpClient
  bindSingleton<KtorClientEngine> { CioClientEngine() }  // the engine
  bindSingleton { GitHubRepos(instance<KtorClientService>()["github"]) }  // a named client
  // ... and the services that use GitHubRepos
}
```

**Koin:**

Include `ktorClientModule()`, which defines the service, adds it to the app services, and defines the default `HttpClient`, and define the engine:

```kotlin
val clientModule = module {
  includes(ktorClientModule())  // KtorClientService, from http.client, and the default HttpClient
  single<KtorClientEngine> { CioClientEngine() }  // the engine
  single { GitHubRepos(get<KtorClientService>()["github"]) }  // a named client
  // ... and the services that use GitHubRepos
}
```

**Metro:**

`boot-client-http-ktor-metro` contributes the service, adds it to the app services, and provides the default `HttpClient`; provide the engine. A graph without one fails to compile with a missing binding.

```kotlin
// KtorClientService and the default HttpClient are contributed by boot-client-http-ktor-metro
@ContributesTo(HttpScope::class)
interface ClientBindings {
  @Provides fun ktorClientEngine(): KtorClientEngine = CioClientEngine()  // the engine

  @Provides @SingleIn(HttpScope::class)
  fun gitHubRepos(clients: KtorClientService): GitHubRepos = GitHubRepos(clients["github"])  // a named client
  // ... and the services that use GitHubRepos
}
```

**Inara:**

Compose `ktorClientModule(…​)`, with the engine, which provides the service and marks it `@AppServices`; a module of your own requires it, to reach its clients:

```kotlin
@DIModule
interface ClientModule : @DIModule.Required KtorClientModule {
  val gitHubRepos: GitHubRepos
  // ... and the services that use GitHubRepos
}

fun clientModule() = ClientModulePart(
  gitHubRepos = Binding.single { GitHubRepos(ktorClientService["github"]) },  // a named client
)

@DIModule(composed = true)
interface ClientAppModule : ConfigModule, BootableModule, KtorClientModule, ClientModule

fun clientAppModule() = ClientAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  ktorClientModule = ktorClientModule(CioClientEngine()),  // ktorClientService, from http.client, on that engine
  clientModule = clientModule(),
)
```

## Customize the clients

For anything the settings don’t cover, such as a plugin, add a `KtorClientCustomizer`. It runs for each client after the typed settings, with the client’s name, or `default`, and Ktor’s own `HttpClientConfig`:

```kotlin
val userAgent = KtorClientCustomizer { name, config ->
  config.install(UserAgent) { agent = "greeting/1.0" }
  if (name == "github") config.expectSuccess = true  // a non-2xx response throws
}
```

Bootable installs no `ContentNegotiation`: which serialization library, and which settings, are the application’s choice, and a customizer is where to install it.

A module that calls a service of its own can contribute its named client’s default settings with a `KtorClientDefaults`, so the application needn’t configure the client. The client exists whether or not the application configures it. The application’s `http.client.named.<name>` settings override the contributed ones, field by field, with `tls` taken whole, and what both leave unset comes from the default client. Two contributors of the same client fail the boot.

**Vanilla:**

Pass the customizer, and the defaults, to the service:

```kotlin
/** A module's own client, `platform`, which trusts the platform's mounted CA certificate unless configured. */
val platformClient = KtorClientDefaults {
  mapOf(
    "platform" to KtorClientConfig(
      baseUrl = "https://platform.internal/",
      tls = KtorClientTlsConfig(trustStore = KeyStoreConfig(path = "/var/run/platform/ca.crt", type = "PEM")),
    ),
  )
}
```

```kotlin
KtorClientService(
  binder.config("http.client"),
  CioClientEngine(),
  userAgent,                          // a customizer
  defaults = listOf(platformClient),  // named clients' default settings
)
```

**Kodein:**

Add them to the sets `ktorClientModule` opens:

```kotlin
inBindSet<KtorClientCustomizer> {
  add {
    singleton {
      KtorClientCustomizer { _, config -> config.install(UserAgent) { agent = "greeting/1.0" } }
    }
  }
}
inBindSet<KtorClientDefaults> {
  add {
    singleton {
      KtorClientDefaults {
        mapOf(
          "platform" to KtorClientConfig(
            baseUrl = "https://platform.internal/",
            tls = KtorClientTlsConfig(trustStore = KeyStoreConfig(path = "/var/run/platform/ca.crt", type = "PEM")),
          ),
        )
      }
    }
  }
}
```

**Koin:**

Define them, each with a qualifier of its own, for `ktorClientModule()` to collect:

```kotlin
single<KtorClientCustomizer>(named("userAgent")) {
  KtorClientCustomizer { _, config -> config.install(UserAgent) { agent = "greeting/1.0" } }
}
single<KtorClientDefaults>(named("platform")) {
  KtorClientDefaults {
    mapOf(
      "platform" to KtorClientConfig(
        baseUrl = "https://platform.internal/",
        tls = KtorClientTlsConfig(trustStore = KeyStoreConfig(path = "/var/run/platform/ca.crt", type = "PEM")),
      ),
    )
  }
}
```

**Metro:**

Contribute them into their sets:

```kotlin
@Provides @IntoSet
fun userAgent(): KtorClientCustomizer =
  KtorClientCustomizer { _, config -> config.install(UserAgent) { agent = "greeting/1.0" } }

@Provides @IntoSet
fun platformClient(): KtorClientDefaults = KtorClientDefaults {
  mapOf(
    "platform" to KtorClientConfig(
      baseUrl = "https://platform.internal/",
      tls = KtorClientTlsConfig(trustStore = KeyStoreConfig(path = "/var/run/platform/ca.crt", type = "PEM")),
    ),
  )
}
```

**Inara:**

Mark them as the sets that `ktorClientModule(…​)` lists, `@KtorClientCustomizers` and `@NamedClientDefaults`:

```kotlin
@DIModule
interface ClientOptionsModule : @DIModule.Required KtorClientModule {
  @KtorClientCustomizers val userAgent: KtorClientCustomizer
  @NamedClientDefaults val platformClient: KtorClientDefaults
}

fun clientOptionsModule() = ClientOptionsModulePart(
  userAgent = Binding.single {
    KtorClientCustomizer { _, config -> config.install(UserAgent) { agent = "greeting/1.0" } }
  },
  platformClient = Binding.single {
    KtorClientDefaults {
      mapOf(
        "platform" to KtorClientConfig(
          baseUrl = "https://platform.internal/",
          tls = KtorClientTlsConfig(trustStore = KeyStoreConfig(path = "/var/run/platform/ca.crt", type = "PEM")),
        ),
      )
    }
  },
)
```

## Telemetry

`boot-client-http-ktor-otel` traces and measures every client’s requests, with OpenTelemetry’s Ktor client instrumentation. Each request is a client span, a child of the span current where the call is made, such as a server request’s, and carries its trace to the server it calls, in a `traceparent` header. Each also records the standard HTTP client metrics, such as `http.client.request.duration`. A request that `retry` retries is a span for each attempt.

It is a `KtorClientCustomizer`, `HttpClientTelemetry`, over the application’s OpenTelemetry, from [`boot-telemetry-otel`](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md).

**Vanilla:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-otel:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel:3.1.0-SNAPSHOT")
}
```

**Kodein:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-otel-kodein:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-otel-koin:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-koin:3.1.0-SNAPSHOT")
}
```

**Metro:**

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-otel-metro:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-metro:3.1.0-SNAPSHOT")
}
```

**Inara:**

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-cio:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-client-http-ktor-otel-inara:3.1.0-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-telemetry-otel-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

`HttpClientTelemetryConfig` reads `telemetry.httpClient`; both its settings are optional, and both are empty by default:

```hocon
telemetry.httpClient {
  capturedRequestHeaders = [X-Correlation-Id]  (1)
  capturedResponseHeaders = [X-Request-Id]     (2)
}
```

- (1) Records these request headers on each request’s span, as `http.request.header.<name>`, so a trace can be found by a value the application sends.
- (2) Records these response headers, as `http.response.header.<name>`, such as the id a server gives a request, to match the span with the server’s own logs.

For an instrumentation setting the config doesn’t cover, such as a span’s name or extra attributes, add an `HttpClientTelemetryCustomizer` to its set binding (Kodein `inBindSet`, Koin a definition with a qualifier of its own, Metro `@IntoSet`), or, with vanilla wiring, pass one to `HttpClientTelemetry`.

**Vanilla:**

Give `HttpClientTelemetry` to the service as its customizer, or, with others, combine them with `KtorClientCustomizer.of(…​)`:

```kotlin
KtorClientService(
  binder.config("http.client"),
  CioClientEngine(),
  // a span for each request, and the HTTP client metrics
  HttpClientTelemetry(telemetry.openTelemetry, binder.config("telemetry.httpClient")),
)
```

**Kodein:**

Import `httpClientTelemetryModule` after `openTelemetryModule` and `ktorClientModule`:

```kotlin
import(openTelemetryModule)
import(clientModule)               // ktorClientModule, and the engine
import(httpClientTelemetryModule)  // after both: a span for each request, and the HTTP client metrics
```

**Koin:**

Include `httpClientTelemetryModule()` with `openTelemetryModule()` and `ktorClientModule()`:

```kotlin
openTelemetryModule(),
clientModule,               // ktorClientModule(), and the engine
httpClientTelemetryModule(),  // a span for each request, and the HTTP client metrics
```

**Metro:**

`boot-client-http-ktor-otel-metro` adds the instrumentation to every client on its own. To change its defaults, contribute an `HttpClientTelemetryCustomizer`:

```kotlin
// boot-client-http-ktor-otel-metro adds the instrumentation to every client; this only changes its span names
@Provides @IntoSet
fun clientSpanNames(): HttpClientTelemetryCustomizer = HttpClientTelemetryCustomizer { builder ->
  // name each span for the host it calls, such as GET api.github.com
  builder.spanNameExtractor { SpanNameExtractor<HttpRequestData> { "${it.method.value} ${it.url.host}" } }
}
```

**Inara:**

Compose `httpClientTelemetryModule()`, which requires the client’s and the OpenTelemetry modules:

```kotlin
@DIModule(composed = true)
interface TracedClientAppModule :
  ConfigModule, BootableModule, ClockModule, OpenTelemetryModule, KtorClientModule, ClientModule,
  HttpClientTelemetryModule

fun tracedClientAppModule() = TracedClientAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  clockModule = clockModule(),
  openTelemetryModule = openTelemetryModule(),
  ktorClientModule = ktorClientModule(CioClientEngine()),
  clientModule = clientModule(),
  // requires the KtorClientModule and an OpenTelemetryApiModule: a span for each request, and the HTTP client metrics
  httpClientTelemetryModule = httpClientTelemetryModule(),
)
```

## Example

`examples/server-ktor-db` has a named client, `entries`, with which `GET /relay/{name}` calls the server back, so one trace shows the request, the client’s call within it, and the server’s handling of that call within that; see [Web / HTTP](https://rocketraman.github.io/bootable/bootable/next/http/index.html.md).

## Reference

- [`KtorClientService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor/com.github.rocketraman.bootable.client.http.ktor/-ktor-client-service/index.html): the clients
- [`HttpClientMilestones`](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor/com.github.rocketraman.bootable.client.http.ktor/-http-client-milestones/index.html): its milestone
- [`KtorClientsConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor/com.github.rocketraman.bootable.client.http.ktor/-ktor-clients-config/index.html): the settings, `http.client`
- [`KtorClientConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor/com.github.rocketraman.bootable.client.http.ktor/-ktor-client-config/index.html): one client’s settings
- [`KtorClientRetryConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor/com.github.rocketraman.bootable.client.http.ktor/-ktor-client-retry-config/index.html): retries
- [`KtorClientTlsConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor/com.github.rocketraman.bootable.client.http.ktor/-ktor-client-tls-config/index.html): TLS
- [`KtorClientEngine`](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor/com.github.rocketraman.bootable.client.http.ktor/-ktor-client-engine/index.html): the engine seam
- [`KtorClientCustomizer`](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor/com.github.rocketraman.bootable.client.http.ktor/-ktor-client-customizer/index.html): for the other settings
- [`KtorClientDefaults`](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor/com.github.rocketraman.bootable.client.http.ktor/-ktor-client-defaults/index.html): a module’s named clients
- [`CioClientEngine`](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor-cio/com.github.rocketraman.bootable.client.http.ktor/-cio-client-engine/index.html) and [`JavaClientEngine`](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor-java/com.github.rocketraman.bootable.client.http.ktor/-java-client-engine/index.html): the engines
- [`HttpClientTelemetry`](https://rocketraman.github.io/bootable/bootable/next/api/boot-client-http-ktor-otel/com.github.rocketraman.bootable.client.http.ktor/-http-client-telemetry/index.html): the telemetry
