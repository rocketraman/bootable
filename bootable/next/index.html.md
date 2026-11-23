# Bootable

A simple, **unopinionated** framework for booting Kotlin applications. It handles starting, stopping, lifecycle management, logging, configuration, and other basic reusable components, and then gets out of your way.

Bootable’s core is plain Kotlin and embeds **no** dependency-injection framework. Its runtime takes two sets, your application services and your lifecycle controllers, and runs them. How you assemble those sets is your choice: wire them by hand, use Bootable’s optional Kodein, Koin, Metro, or Inara bindings, or bring your own DI.

Coming from Spring Boot? See [Bootable and Spring Boot](https://rocketraman.github.io/bootable/bootable/next/comparison/spring-boot.html.md) for how the two differ, and when each fits.

## Get started

Pick a wiring style. Every page on this site shows its code in the style you pick, and remembers it.

- [Vanilla](https://rocketraman.github.io/bootable/bootable/next/wiring.html.md) Plain Kotlin, no DI framework.
- [Kodein](https://rocketraman.github.io/bootable/bootable/next/wiring.html.md) A runtime DI with a clean DSL; deprecated: consider Inara or Koin.
- [Koin](https://rocketraman.github.io/bootable/bootable/next/wiring.html.md) A runtime DI with a DSL like Kodein’s.
- [Metro](https://rocketraman.github.io/bootable/bootable/next/wiring.html.md) A compile-time DI that checks the graph as it builds.
- [Inara](https://rocketraman.github.io/bootable/bootable/next/wiring.html.md) Kodein’s compile-time successor, checked by KSP; experimental.

## Functions

- [Configuration](https://rocketraman.github.io/bootable/bootable/next/configuration/index.html.md) Typed config from HOCON and the environment, with secrets masked.
- [Environment](https://rocketraman.github.io/bootable/bootable/next/environment/index.html.md) The deployment environment, such as dev or prod, from config.
- [Logging](https://rocketraman.github.io/bootable/bootable/next/logging/index.html.md) Log4j2, with plain, coloured, JSON, and Google Cloud output.
- [Database](https://rocketraman.github.io/bootable/bootable/next/database/index.html.md) A lifecycle-managed connection pool, migrations, and Exposed.
- [Web / HTTP](https://rocketraman.github.io/bootable/bootable/next/http/index.html.md) A Ktor server and clients, with liveness and readiness probes.
- [Background work](https://rocketraman.github.io/bootable/bootable/next/background/index.html.md) Periodic loops, leader election, and persistent scheduled tasks.
- [Telemetry](https://rocketraman.github.io/bootable/bootable/next/telemetry/index.html.md) OpenTelemetry traces and metrics, pushed or pulled.
- [Testing](https://rocketraman.github.io/bootable/bootable/next/testing/index.html.md) Helpers for fast unit tests, with no application booted.

## Examples

Runnable applications that put these functions together; see [Examples](https://rocketraman.github.io/bootable/bootable/next/examples/index.html.md).

## For LLMs

Each page is also published as Markdown, at its URL with `.md` appended, and [llms.txt](https://rocketraman.github.io/bootable/bootable/next/llms.txt) indexes them, as [llmstxt.org](https://llmstxt.org/) describes. [llms-full.txt](https://rocketraman.github.io/bootable/bootable/next/llms-full.txt) has every page in one file.
