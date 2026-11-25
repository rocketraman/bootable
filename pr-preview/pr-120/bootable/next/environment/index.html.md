# Environment

> The deployment environment, such as dev, test, or prod, from config, in a global variable.

`boot-env` sets a global variable, `environment`, to the environment the application is deployed in: `Env.Dev`, `Env.Test`, `Env.Prod`, or `Env.Custom` with any other name. It reads it from the `env.environment` config key.

> **Warning:** Over-using this is a code smell. Code that asks which environment it runs in rather than reading a setting from config is harder to reason about, and can have unintended consequences.

Use it rarely for cross-cutting concerns where sharing a configuration setting would be invasive, or for environment-specific config too complex to express in the [config classes](https://rocketraman.github.io/bootable/bootable/next/configuration/index.html.md). For example:

- Tagging metrics, traces, or logs with the environment.
- Refusing to start in `Env.Prod` with settings meant only for development, such as a default password, TLS turned off, or a SQLite database. A flag in the config couldn’t guard against these, since a config that is wrong for prod would be as likely to have the flag wrong too.

`environment` is set by a lifecycle controller, `EnvironmentInitializer`, in its `appInit`, which runs once the application is built, before any service starts. It has the highest priority, so it runs before every other controller’s `appInit`. Read `environment` from a service’s `start`, or later; read while the application is being built, such as in a constructor or a config binding, it isn’t set yet, and throws.

## Modules

| Module | What it provides |
| --- | --- |
| `boot-env` | `environment`: the deployment environment, such as dev or prod, from config |

## Install

**Vanilla:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-env:3.1.0-SNAPSHOT")
}
```

**Kodein:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-env-kodein:3.1.0-SNAPSHOT")
}
```

**Koin:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-env-koin:3.1.0-SNAPSHOT")
}
```

**Metro:**

```kotlin
plugins {
  id("dev.zacsweers.metro") version "1.4.5"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-env-metro:3.1.0-SNAPSHOT")
}
```

**Inara:**

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot-env-inara:3.1.0-SNAPSHOT")
  ksp("org.kodein.di.inara:inara-processor:0.4.0")
}
```

`boot-env` reads its config with a `ConfigBinder`, such as [`boot-config-hoplite`](https://rocketraman.github.io/bootable/bootable/next/configuration/index.html.md)'s.

## Configure

```hocon
env {
  environment = prod  (1)
}
```

- (1) `dev`, `test`, or `prod`, in any case, or any other name, which is an `Env.Custom` of that name. The key is required.

As an environment variable, it is `ENV_ENVIRONMENT=prod`.

## Wire

**Vanilla:**

Add `environmentInitializer(binder)` to the lifecycle controllers you pass to `Bootable`:

```kotlin
fun main() = boot {
  val binder = hopliteConfigBinder()
  Bootable(
    appServices = setOf(/* ... the application's services */),
    lifecycleControllers = defaultLifecycleControllers() + environmentInitializer(binder),
  )
}
```

**Kodein:**

Import `environmentModule`, which binds the `EnvironmentConfig` and adds the initializer to the lifecycle controllers:

```kotlin
fun appDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(environmentModule)  // sets environment from env.environment, before services start
  // ... the application's services
}

fun main() = boot {
  appDI().direct.instance<Bootable>()
}
```

**Koin:**

Include `environmentModule()`, which defines the `EnvironmentConfig` and adds the initializer to the lifecycle controllers:

```kotlin
fun appKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    environmentModule(),  // sets environment from env.environment, before services start
    // ... the application's services
  )
}.koin

fun main() = boot {
  appKoin().get<Bootable>()
}
```

**Metro:**

`boot-env-metro` contributes the `EnvironmentConfig` and the initializer on their own: a graph that discovers Bootable’s bindings, with `additionalScopes = [BootScope::class]`, needs nothing more.

A graph that names its binding containers instead names `EnvironmentConfigBindings` and `EnvironmentBindings`:

```kotlin
@DependencyGraph(
  EnvironmentScope::class,
  bindingContainers = [
    BootableDefaultBindings::class,
    HopliteConfigBindings::class,
    EnvironmentConfigBindings::class,  // EnvironmentConfig, from env
    EnvironmentBindings::class,        // the initializer, in the lifecycle controllers
  ],
)
interface EnvironmentGraph : BootableGraph
```

**Inara:**

Compose `environmentModule()`, which provides the `EnvironmentConfig` and adds the initializer to the lifecycle controllers:

```kotlin
@DIModule(composed = true)
interface AppModule : ConfigModule, BootableModule, EnvironmentModule  // ... and the application's services

fun appModule() = AppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  environmentModule = environmentModule(),  // sets environment from env.environment, before services start
)

fun main() = boot {
  appModule().bootable
}
```

## Use it

`Env` is a sealed class, so a `when` over it covers every case:

```kotlin
/** The environment, as a tag on the application's metrics. */
fun environmentTag(): String = when (val env = environment) {
  Env.Dev -> "dev"
  Env.Test -> "test"
  Env.Prod -> "prod"
  is Env.Custom -> env.name
}
```

[Telemetry](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md) reads the same `env.environment` key, for its resource’s `deployment.environment.name`, unless its own config sets one.

## Reference

- [`environment`](https://rocketraman.github.io/bootable/bootable/next/api/boot-env/com.github.rocketraman.bootable.env/environment.html): the global variable
- [`Env`](https://rocketraman.github.io/bootable/bootable/next/api/boot-env/com.github.rocketraman.bootable.env/-env/index.html): the environments
- [`environmentInitializer`](https://rocketraman.github.io/bootable/bootable/next/api/boot-env/com.github.rocketraman.bootable.env/environment-initializer.html): the lifecycle controller that sets it, for vanilla wiring
- [`EnvironmentConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-env/com.github.rocketraman.bootable.env/-environment-config/index.html): the `env` config section
