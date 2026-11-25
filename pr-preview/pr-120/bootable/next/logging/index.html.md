# Logging (Log4j2)

> Log4j2 to the console, as plain or coloured text, JSON, or Google Cloud's JSON, set up before anything else runs.

`boot-logging-log4j2` makes [Log4j2](https://logging.apache.org/log4j/2.x/) the application’s logging, with a console appender in one of several output types: coloured or plain text, JSON, or Google Cloud’s structured JSON. It routes the other logging APIs to Log4j2 too: SLF4J, `java.util.logging`, and the standard output and error streams.

`boot { }` initializes logging before anything else, before it builds the application, so config loading and the application’s construction log with the chosen output. Logging isn’t a service, and isn’t part of the lifecycle; it is set up before the lifecycle begins.

## Modules

`boot-logging` holds `BootLoggingLevel`, the log levels, with no logging library. It comes with `boot-logging-log4j2`.

| Module | What it provides |
| --- | --- |
| **Log4j2: comes with `boot`; add it yourself to call its API, such as `LoggingType`** |  |
| `boot-logging-log4j2` | Log4j2, with layouts for plain, coloured, JSON, and Google Cloud output |
| **Core: comes with Log4j2** |  |
| `boot-logging` | `BootLoggingLevel` |

## Install

`boot` brings `boot-logging-log4j2` at runtime, since `boot { }` initializes it. Add it as a dependency to use its API in your code: `LoggingType`, `loggingInit`, and Log4j’s Kotlin API.

**Vanilla:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-logging-log4j2:3.1.0-SNAPSHOT")
}
```

**Kodein:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-logging-log4j2:3.1.0-SNAPSHOT")
}
```

**Koin:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-logging-log4j2:3.1.0-SNAPSHOT")
}
```

**Metro:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-logging-log4j2:3.1.0-SNAPSHOT")
}
```

**Inara:**

```kotlin
dependencies {
  implementation("com.github.rocketraman.bootable:boot-logging-log4j2:3.1.0-SNAPSHOT")
}
```

It has no DI flavours: logging is set up before the application’s graph is built, so the wiring is the same in every style.

## Log

Log through Log4j’s [Kotlin API](https://logging.apache.org/log4j/kotlin/), whose `logger()` names the logger for the class it is created in:

```kotlin
class Greeter {
  private val log = logger()

  fun greet(name: String) = log.info { "greeting $name" }
}
```

A library that logs through SLF4J or `java.util.logging` logs through Log4j2 too, with the same output.

## Output types

`LoggingType` chooses the output:

| `LoggingType` | Output |
| --- | --- |
| `DEFAULT`, `COLOR` | Text, one line for each event, with the level and logger in ANSI colours. `LOGGING_ANSI_DISABLE=true` turns the colours off. |
| `PLAIN` | The same text, without colours. |
| `JSON` | One JSON object for each event, in Log4j’s JSON template layout’s default, Elastic Common Schema, layout. |
| `GCLOUD` | One JSON object for each event, in the layout Google Cloud Logging reads as structured logs: the severity in Google Cloud’s levels, and the trace and span from the logging context’s `trace_id` and `span_id`, so Cloud Logging links each event to its trace. |
| `EMPTY` | Nothing: every event is discarded. |

`DEFAULT` is the type when none is chosen, and is the same output as `COLOR`.

Choose the type, in order of precedence:

1. as an argument, `boot(loggingType = …​)`, or `loggingInit(loggingType = …​)`;
2. with the `LOGGING_TYPE` environment variable, such as `LOGGING_TYPE=json`, in any case; a value that isn’t a `LoggingType` logs a warning, and the default is used;
3. or leave it to the default.

```kotlin
fun main() = boot(loggingType = LoggingType.JSON) {  // or leave it to LOGGING_TYPE
  Bootable(setOf(/* ... the application's services */))
}
```

Choosing the type in the environment lets one build log text on a developer’s machine and JSON in production.

## Settings

The output types read these environment variables, or the system properties after them, at the start:

| Environment variable | System property | Default | Sets |
| --- | --- | --- | --- |
| `LOGGING_LEVEL` | `logging.level` | `info` | The root logger’s level: `trace`, `debug`, `info`, `warn`, `error`, `fatal`, or `off`. |
| `LOGGING_ANSI_DISABLE` | `logging.ansi.disable` | `false` | Whether `COLOR` and `DEFAULT` leave out the colours. |
| `LOGGING_LOCATION_ENABLED` | `logging.location.enabled` | `false` | Whether `JSON` and `GCLOUD` record each event’s source location, its class, method, and line, which is costly. |
| `LOGGING_CONSOLE_DIRECT` | `logging.console.direct` | `true` | Whether the console appender writes to the process’s standard output directly, rather than through `System.out`. Leave it on while [the standard streams are redirected](https://rocketraman.github.io/bootable/bootable/next/logging/index.html.md#standard-streams); off, the appender can race with the redirection. |

An environment variable wins over its system property.

### Log levels

`LOGGING_LEVEL` sets the root logger’s level, which every logger has unless it sets its own. For the level of one logger, or of a package, add a `log4j2.xml` of your own; see [Your own Log4j2 config](https://rocketraman.github.io/bootable/bootable/next/logging/index.html.md#your-config).

`loggingInit(overrideLoggingLevel = …​)` takes a `BootLoggingLevel` as the default for the root level, in place of `info`; `LOGGING_LEVEL` and `logging.level` still win over it.

## Your own Log4j2 config

Bootable’s config for the output type is the base, and merged over it, if they are on the classpath, are:

1. `log4j2-<type>.xml`, for one output type only, such as `log4j2-json.xml`;
2. `log4j2.xml`, for every type.

Each is a Log4j2 configuration that adds to the base or changes it, such as a logger’s level. The base’s appender is named `Console`, so a logger can refer to it:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="error">
  <Loggers>
    <Logger name="com.example.orders" level="debug" additivity="false">
      <AppenderRef ref="Console"/>
    </Logger>
    <Logger name="org.eclipse.jetty" level="warn"/>
  </Loggers>
</Configuration>
```

`loggingInit(allowMerging = false)` uses the base alone. To replace Bootable’s config altogether, set the `log4j.configurationFile` system property: Bootable sets it only when it isn’t set.

## Standard output and error

Once logging is initialized, what is written to `System.out` is logged by the `STDOUT` logger at `INFO`, and what is written to `System.err` by the `STDERR` logger at `WARN`, so a library that prints rather than logs still logs in the chosen output.

## Command-line tools

A command-line tool that doesn’t call `boot { }` calls `loggingInit` itself, first, before it creates a logger. It should keep its standard output for its own output, and may choose a type and a level:

```kotlin
fun cliMain() {
  loggingInit(
    redirectStandardOutErr = false,             // keep standard output for the tool's own output
    loggingType = LoggingType.PLAIN,
    overrideLoggingLevel = BootLoggingLevel.WARN,
  )
  // ... the tool's work
}
```

Logging is initialized once: a second call to `loggingInit`, such as the one `boot { }` makes, does nothing. So an application that calls `loggingInit` before `boot { }`, to pass a level or turn off the redirection, gets its own settings.

## Example

`examples/wiring-vanilla`, `examples/wiring-kodein`, `examples/wiring-koin`, and `examples/wiring-metro` each have a `log4j2.xml` that sets Bootable’s own loggers to `info`, merged over the base. From the repository root, run one with JSON output:

```shell
LOGGING_TYPE=json ./gradlew :examples:wiring-vanilla:run
```

## Reference

- [`LoggingType`](https://rocketraman.github.io/bootable/bootable/next/api/boot-logging-log4j2/com.github.rocketraman.bootable.logging.log4j2/-logging-type/index.html): the output types
- [`loggingInit`](https://rocketraman.github.io/bootable/bootable/next/api/boot-logging-log4j2/com.github.rocketraman.bootable.logging.log4j2/logging-init.html): initializes logging, as `boot { }` does
- [`BootLoggingLevel`](https://rocketraman.github.io/bootable/bootable/next/api/boot-logging/com.github.rocketraman.bootable.logging/-boot-logging-level/index.html): the log levels
- [Log4j2’s configuration](https://logging.apache.org/log4j/2.x/manual/configuration.html)
