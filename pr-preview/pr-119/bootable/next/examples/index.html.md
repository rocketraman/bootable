# Examples

> Runnable applications in the repository's examples directory, each showing Bootable functions put together.

Each example is a small application in the repository’s [`examples`](https://github.com/rocketraman/bootable/tree/master/examples) directory, built against the Bootable modules in the same repository. Run any of them from the repository root with Gradle; they are services, so they run until you stop them with Ctrl-C.

- [Wiring styles](https://rocketraman.github.io/bootable/bootable/next/examples/wiring.html.md): the smallest application, one service that logs a greeting from config, wired five ways (vanilla, Kodein, Koin, Metro, and Inara).
- [Ktor server](https://rocketraman.github.io/bootable/bootable/next/examples/server-ktor.html.md): a Ktor server on Netty with typed config, which can also be built as a GraalVM native image; vanilla wiring.
- [Server with a database](https://rocketraman.github.io/bootable/bootable/next/examples/server-ktor-db.html.md): a key/value store over HTTP with the database, telemetry, an HTTP client, health probes, and background work, wired five ways (vanilla, Kodein, Koin, Metro, and Inara), with a container image that has a JVM AOT cache.
- [Custom start order](https://rocketraman.github.io/bootable/bootable/next/examples/start-order.html.md): a Ktor server and a ready file, with a custom lifecycle phase; vanilla wiring.
- [Web app with Compose HTML](https://rocketraman.github.io/bootable/bootable/next/examples/compose-html.html.md): a todo list with a Compose HTML browser client and a Ktor server, sharing their models; vanilla wiring.
- [Web app with htmx](https://rocketraman.github.io/bootable/bootable/next/examples/htmx.html.md): the same todo list, rendered on the server with kotlinx.html and made interactive with htmx; vanilla wiring.
- [Web app with Kilua](https://rocketraman.github.io/bootable/bootable/next/examples/kilua.html.md): the same todo list with a Kilua client, rendered on the server by a Node.js process that Bootable starts before the Ktor server; vanilla wiring.

`examples/di-bench` is not an example of Bootable: it is a benchmark of how long Kodein, Koin, and Metro take to build graphs of 25, 100, and 400 services, and doesn’t use Bootable.
