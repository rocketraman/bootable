# Web app with Compose HTML

> A todo list in Kotlin end to end, with a Compose HTML browser client and a Ktor server run by Bootable.

`compose-html` is the same todo list as [htmx](https://rocketraman.github.io/bootable/bootable/next/examples/htmx.html.md) and [Kilua](https://rocketraman.github.io/bootable/bootable/next/examples/kilua.html.md), written in Kotlin end to end: a browser client written with [Compose HTML](https://github.com/JetBrains/compose-multiplatform#compose-html), and a Ktor server run by Bootable. The server serves the client’s files and a JSON API, so the whole application runs as one process and one jar.

It is in three projects:

**`shared`**

The API’s models (`Todo`, `NewTodo`, `TodoUpdate`), its path, and the rule for a valid title, in a Kotlin Multiplatform module compiled for the browser and the JVM, so the client and the server agree on the JSON and on what makes a todo valid.

**`client`**

The Compose HTML UI, compiled to JavaScript, which calls the API with Ktor’s client, and checks a title with the shared rule as the user types.

**`server`**

`TodoServer`, a `NettyKtorService`, which serves the JSON API under `/api/todos`, and the client’s files at `/`, and checks every title with the same shared rule.

Todos are kept in memory; [Server with a database](https://rocketraman.github.io/bootable/bootable/next/examples/server-ktor-db.html.md) stores data in a database.

The same application is also written with [htmx](https://rocketraman.github.io/bootable/bootable/next/examples/htmx.html.md), rendered on the server, and with [Kilua](https://rocketraman.github.io/bootable/bootable/next/examples/kilua.html.md), rendered on the server and then in the browser.

## What it uses

- `boot-server-http-ktor-netty`, for the server on Ktor’s Netty engine; see [Server (Ktor)](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md).
- `boot-config-hoplite`, for the `server` config section; see [Configuration](https://rocketraman.github.io/bootable/bootable/next/configuration/index.html.md).
- `boot-logging-log4j2`, for the log; see [Logging (Log4j2)](https://rocketraman.github.io/bootable/bootable/next/logging/index.html.md).

The server is the only service, wired with plain Kotlin:

```kotlin
fun appServices(binder: ConfigBinder): Set<AppService> = setOf(
  TodoServer(binder.config("server"), TodoRepository()),
)

fun main() = boot {
  Bootable(appServices = appServices(hopliteConfigBinder()))
}
```

## Run it

From the repository root:

```shell
./gradlew :examples:compose-html:server:run
```

Then open [http://localhost:8002](http://localhost:8002).

The server’s build copies the client’s production bundle onto its classpath, under `web/`, so this one command builds the client too. The server’s jar and distribution (`installDist`, `distZip`) contain the client, and need nothing else to run.

### Live reload

For changes to the UI, run the client from Kotlin/JS’s webpack development server, which reloads the page whenever the client is rebuilt. Run the server in one terminal, for the API, and the development server in another:

```shell
./gradlew :examples:compose-html:server:run
./gradlew --continuous :examples:compose-html:client:jsBrowserDevelopmentRun
```

Then open [http://localhost:8080](http://localhost:8080), webpack’s port, which forwards requests under `/api` to the server. Changes to `client` or `shared` are picked up without a restart; changes to `server` still need one.

## Tests

```shell
./gradlew :examples:compose-html:server:test
```

The tests run the server’s routes in Ktor’s test host, with a client that sends and receives the shared models, and check that the server serves the client’s bundle.

## Source

[`examples/compose-html`](https://github.com/rocketraman/bootable/tree/master/examples/compose-html); its README also covers styling a Compose HTML application.
