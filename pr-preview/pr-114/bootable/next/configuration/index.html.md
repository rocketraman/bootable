# Configuration

> Typed config classes, bound from HOCON and the environment by Hoplite, with secrets masked.

An application’s config is a set of typed classes, each bound from a section of the config by a `ConfigBinder`. `boot-config-common` defines the `ConfigBinder`, which Bootable’s own modules read their config with, and `boot-config-hoplite` implements it with [Hoplite](https://github.com/sksamuel/hoplite), from HOCON files, system properties, and the environment.

Config isn’t a service, so it has no lifecycle phase of its own. It is bound while the application is built, in the `boot { }` block, which runs after logging is initialized, so a config error is logged, and fails the start before any service starts.

## Modules

An application adds the config library, `boot-config-hoplite`. `boot-config-common` comes with it, and with every module that reads config.

| Module | What it provides |
| --- | --- |
| **Config library: choose one** |  |
| `boot-config-hoplite` | `ConfigBinder` over Hoplite, from HOCON and the environment |
| **Common: comes with the config library, and the modules that read config** |  |
| `boot-config-common` | `ConfigBinder`, and `Masked`, for secrets in config |

## Install

**Vanilla:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-config-hoplite:3.1.0-SNAPSHOT")
}
```

**Kodein:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-config-hoplite-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-config-hoplite-koin:3.1.0-SNAPSHOT")
}
```

**Metro:**

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-config-hoplite-metro:3.1.0-SNAPSHOT")
}
```

**Inara:**

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.13"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-config-hoplite-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.3.0")
}
```

## Configure

Hoplite reads these sources, each overriding the ones below it:

1. Environment variables, such as `GREETING_MESSAGE` for `greeting.message`; see [Environment variables](https://rocketraman.github.io/bootable/bootable/next/configuration/index.html.md#environment-variables).
2. System properties with the `config.override.` prefix, such as `-Dconfig.override.greeting.message=hi`.
3. `~/.userconfig.conf`, in the user’s home directory, and `$XDG_CONFIG_HOME/hoplite.conf`, if they exist.
4. `application-local.conf`, on the classpath, if it exists: for settings on a developer’s machine, kept out of version control.
5. `application.conf`, on the classpath, which must exist.

Each config class reads one section, named by its path:

```hocon
greeting {
  message = "hello from Bootable"
  apiKey = ${?GREETING_API_KEY}  (1)
}

server {
  host-port = ":8080"  (2)
}
```

- (1) From the environment variable, if it is set; see [Substitutions](https://rocketraman.github.io/bootable/bootable/next/configuration/index.html.md#substitutions).
- (2) A key matches its property in any case, and with or without dashes, so `host-port`, as here, and `hostPort`, as the property is spelled, are the same key.

### Config classes

A config class is a data class, with a property for each key in its section. A property with a default, or a nullable one, is optional; any other fails the bind when its key is missing.

```kotlin
data class GreetingConfig(
  val message: String,
  val apiKey: Secret,                  // masked in the config report
  val retries: Int = 3,                // optional, with its default
)

data class ServerConfig(
  override val hostPort: String,       // ":8080", "localhost:8080", or "localhost"
) : HostPort
```

`HostPort`, from `boot-config-common`, is a `hostPort` string, such as `localhost:8080` or `:8080`, with helpers to read it: `host()`, which defaults to `0.0.0.0`, `port(default)`, `portOrNull()`, and `inetAddress()`. The Ktor server takes its address as a `HostPort`.

Hoplite decodes the usual types: strings, numbers, booleans, durations such as `5s`, lists, sets, maps, and nested data classes. An enum value matches its entry’s name in any case, so `fail`, `Fail`, and `FAIL` are all `Fail`. A sealed type is chosen by its subtype’s name, in a `_type` key next to the subtype’s properties, such as `tiebreaker { _type = Shuffled, seed = 42 }`.

### Secrets

The config classes of Bootable’s own modules declare a secret, such as a password, an API key, or an auth header, as `Masked`, from `boot-config-common`. Their config classes depend only on `boot-config-common`, not on a config library, so they can’t use the library’s own type for secrets. A `Masked`'s `toString` is `****`, so a config class that holds one prints safely; read the secret with `value`. `boot-config-hoplite` decodes a `Masked` from a string or a number.

In your own config classes, declare a secret with Hoplite’s `Secret`, as `GreetingConfig.apiKey` does above. Bootable doesn’t aim to make config libraries interchangeable, so `Masked` is for its own modules.

### The config report

When it binds a section, `boot-config-hoplite` prints a report of the section’s keys, each with its value and where it came from, and the keys in it that its config class doesn’t read. It prints to standard output, which Bootable [logs](https://rocketraman.github.io/bootable/bootable/next/logging/index.html.md#standard-streams), once logging is initialized. The report masks these values:

- a `Masked` value, wherever it is: in a data class, a map, or a list;
- a Hoplite `Secret`;
- a value whose name contains `secret`, `credential`, or `pass`;
- a value its source marks as a secret.

The report shows values as they were loaded, before Hoplite’s resolvers run, so it shows `${{ env:DB_PASSWORD }}`, not the password. Turn it off with `report = false`; see [Customize the binder](https://rocketraman.github.io/bootable/bootable/next/configuration/index.html.md#customize).

### Environment variables

An environment variable overrides the key whose path it spells, in upper case, with `_` in place of each `.`: `GREETING_MESSAGE` sets `greeting.message`. A camel case key is spelled without a separator: `DB_POOL_MAXIMUMPOOLSIZE` sets `db.pool.maximumPoolSize`.

An environment variable overrides a map entry, or a property of one, keeping the entry’s key as config spells it. With `http.client.named.myClient { baseUrl = "http://localhost:8001/" }` in config, `HTTP_CLIENT_NAMED_MYCLIENT_BASEURL=http://localhost:8002/` changes the `baseUrl` of the `myClient` client, rather than adding a client named `MYCLIENT`. An entry that only the environment defines is keyed as the environment variable spells it, in upper case.

### Substitutions

A value can name an environment variable, or another value, in two ways.

HOCON’s own substitutions work anywhere, in maps and lists too, and are resolved when HOCON parses the file:

```hocon
db.jdbc.dataSourceProperties {
  sslmode = ${?DB_SSLMODE}  (1)
}
greeting.message = ${app.name}  (2)
```

- (1) The environment variable, if it is set; without it, the key is left unset, so a property with a default keeps it. `${DB_SSLMODE}`, without the `?`, fails the parse when the variable isn’t set.
- (2) Another value in the config.

Hoplite’s resolvers name an environment variable, a system property, or another value, with a fallback if you like:

```hocon
db.jdbc {
  host = "${{ env:DB_HOST }}"
  port = "${{ env:DB_PORT:-5432 }}"  (1)
  username = "${{ sysprop:db.user }}"
  database = "${{ ref:app.name }}"
}
```

- (1) The fallback, after `:-`, when the variable isn’t set.

A resolver that resolves to nothing, and has no fallback, fails the bind, even for a property with a default. A resolver works only in a property whose value is a single string, which can then be decoded as another type, such as the `Int` port above. In the entries of a map, a list, or a set, it is left as literal text: use HOCON’s `${?VAR}` there.

Hoplite’s older preprocessor syntax, `${VAR}` in a quoted string, or `{{key}}`, is not expanded, and is left as literal text.

### Bootable’s own config

Each Bootable module reads its own section, such as `db` for the database, `http.client` for the HTTP clients, `telemetry` for OpenTelemetry, or `env` for the environment. A module’s page shows its section; [the database’s](https://rocketraman.github.io/bootable/bootable/next/database/index.html.md#_configure), for example. With a DI, a module’s bindings bind its config from the graph’s `ConfigBinder`; wired by hand, you bind it, and pass it to the module’s constructor.

Bootable itself reads two optional sections, if there is a `ConfigBinder`: `boot.service.order`, how services the start order leaves tied are started, and `health.readiness`, the readiness checks' settings.

## Wire

**Vanilla:**

Build the binder with `hopliteConfigBinder()`, and bind each section with `config<T>(path)`:

```kotlin
fun main() = boot {
  val binder = hopliteConfigBinder()
  Bootable(
    setOf(GreetingService(binder.config<GreetingConfig>("greeting"))),
  )
}
```

**Kodein:**

Import `hopliteConfigModule`, which binds the `ConfigBinder`, and bind each section from it:

```kotlin
fun appDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)  // binds the ConfigBinder
  bindSingleton { instance<ConfigBinder>().config<GreetingConfig>("greeting") }
  bindAppService { singleton { new(::GreetingService) } }
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
```

**Koin:**

Include `hopliteConfigModule()`, which defines the `ConfigBinder`, and define each section from it:

```kotlin
fun appKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),  // defines the ConfigBinder
    module {
      single { get<ConfigBinder>().config<GreetingConfig>("greeting") }
      single<AppService>(named<GreetingService>()) { GreetingService(get()) }
    },
  )
}.koin

fun main() = boot {
  appKoin().get<Bootable>()
}
```

**Metro:**

`boot-config-hoplite-metro` contributes the `ConfigBinder` on its own. Bind each section in a contributed interface that extends `ConfigBinderProvider`, which gives it the binder, so each binding is a type and a path:

```kotlin
// boot-config-hoplite-metro contributes the ConfigBinder; each config binding is a type and a key
@ContributesTo(ConfigurationScope::class)
interface GreetingConfigBindings : ConfigBinderProvider {
  @Provides fun greetingConfig(): GreetingConfig = config("greeting")
}

@DependencyGraph(ConfigurationScope::class, additionalScopes = [BootScope::class])
interface ConfigurationGraph : BootableGraph

fun main() = boot {
  createGraph<ConfigurationGraph>().bootable
}
```

A graph without a `ConfigBinder` fails to compile with a missing binding.

**Inara:**

Compose `hopliteConfigModule()`, the `ConfigModule` every other module reads, and bind each section with `config(…​)`:

```kotlin
@DIModule(composed = true)
interface AppModule : ConfigModule, BootableModule {
  val greetingConfig: GreetingConfig
  @AppServices val greetingService: GreetingService
}

fun appModule() = AppModule(
  configModule = hopliteConfigModule(),  // provides the ConfigBinder
  bootableModule = bootableModule(),
  greetingConfig = config("greeting"),   // decoded once, from the ConfigBinder
  greetingService = Binding.single { GreetingService(greetingConfig) },
)

fun main() = boot {
  appModule().bootable
}
```

## Customize the binder

`hopliteConfigBinder` takes optional parameters:

- `withConfigStage1`, `withConfigStage2`, `withConfigStage3`: hooks on Hoplite’s `ConfigLoaderBuilder`, to add sources, resolvers, or anything else. Sources added in stage 1 override the environment variables; in stage 2, they are between the user settings and `application-local.conf`; in stage 3, below `application.conf`. Hoplite’s modules for secret stores, such as Vault, AWS Secrets Manager, GCP Secret Manager, and Azure Key Vault, add their resolvers this way, with `addResolver`.
- `decoders`: Hoplite decoders for your own types, which can also be registered with Java’s `ServiceLoader`.
- `report`: whether to print [the config report](https://rocketraman.github.io/bootable/bootable/next/configuration/index.html.md#report); `true` by default.
- `secretsPolicy` and `obfuscator`: which values the report masks, and how; Hoplite’s `StandardSecretsPolicy` and `StrictObfuscator` by default. A `Masked` value is masked whatever the policy.
- `strict`: fail a bind when its section has a key the config class has no property for; `false` by default.
- `environmentVariables`: the environment variables, for the environment variable source and `${{ env:VAR }}`; `System.getenv()` by default. HOCON’s own `${?VAR}` substitutions read the process environment, not these.

**Vanilla:**

```kotlin
fun customBinder(): ConfigBinder = hopliteConfigBinder(
  // above application.conf, below everything else
  withConfigStage2 = { addPropertySource(PropertySource.file(java.io.File("/etc/greeter/greeter.conf"), optional = true)) },
  report = false,  // no config report at start
  strict = true,   // fail on a key in a section that its config class has no property for
)
```

**Kodein:**

Bind your own `ConfigBinder`, in place of `hopliteConfigModule`:

```kotlin
fun customDI() = DI {
  import(bootableModule)
  // in place of hopliteConfigModule
  bindSingleton<ConfigBinder> { hopliteConfigBinder(report = false, strict = true) }
}
```

**Koin:**

Define your own `ConfigBinder`, in place of `hopliteConfigModule()`:

```kotlin
fun customKoin() = koinApplication {
  modules(
    bootableModule(),
    // in place of hopliteConfigModule()
    module { single<ConfigBinder> { hopliteConfigBinder(report = false, strict = true) } },
  )
}.koin
```

**Metro:**

Provide your own `ConfigBinder`, and name Bootable’s binding containers in the graph, rather than discovering them, so `HopliteConfigBindings`'s default isn’t in the graph too:

```kotlin
@BindingContainer
object CustomConfigBinder {
  @Provides @SingleIn(BootScope::class)
  fun configBinder(): ConfigBinder = hopliteConfigBinder(report = false, strict = true)
}

// Bootable's bindings named, not discovered, so HopliteConfigBindings' default ConfigBinder isn't in the graph
@DependencyGraph(ConfigurationScope::class, bindingContainers = [BootableDefaultBindings::class, CustomConfigBinder::class])
interface CustomConfigGraph : BootableGraph
```

A container named this way isn’t discovered, so name every one the graph needs, such as `EnvironmentBindings` for [the environment](https://rocketraman.github.io/bootable/bootable/next/environment/index.html.md).

**Inara:**

Build your own `ConfigModule`, and pass it in place of `hopliteConfigModule()`:

```kotlin
// passed as the configModule, in place of hopliteConfigModule()
val customConfigModule = ConfigModule(
  configBinder = Binding.single { hopliteConfigBinder(report = false, strict = true) },
)
```

## Example

`examples/wiring-vanilla`, `examples/wiring-kodein`, `examples/wiring-koin`, and `examples/wiring-metro` each bind a `greeting` section from `application.conf`, and log it when the application starts. From the repository root, run yours:

**Vanilla:**

```shell
./gradlew :examples:wiring-vanilla:run
```

**Kodein:**

```shell
./gradlew :examples:wiring-kodein:run
```

**Koin:**

```shell
./gradlew :examples:wiring-koin:run
```

**Metro:**

```shell
./gradlew :examples:wiring-metro:run
```

**Inara:**

```shell
./gradlew :examples:wiring-inara:run
```

It logs `greeting: hello from Bootable`; try it with `GREETING_MESSAGE=hi` set.

## Reference

- [`ConfigBinder`](https://rocketraman.github.io/bootable/bootable/next/api/boot-config-common/com.github.rocketraman.bootable.config/-config-binder/index.html): binds a config section to a type
- [`config`](https://rocketraman.github.io/bootable/bootable/next/api/boot-config-common/com.github.rocketraman.bootable.config/config.html): `config<T>(path)`
- [`Masked`](https://rocketraman.github.io/bootable/bootable/next/api/boot-config-common/com.github.rocketraman.bootable.config/-masked/index.html): a secret in Bootable’s own config classes
- [`HostPort`](https://rocketraman.github.io/bootable/bootable/next/api/boot-config-common/com.github.rocketraman.bootable.config.common/-host-port/index.html): a host and port
- [`hopliteConfigBinder`](https://rocketraman.github.io/bootable/bootable/next/api/boot-config-hoplite/com.github.rocketraman.bootable.config/hoplite-config-binder.html): the Hoplite binder, and its parameters
- [Hoplite](https://github.com/sksamuel/hoplite)'s own documentation
