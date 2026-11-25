# Web app with htmx

> A todo list rendered on the server with kotlinx.html, and made interactive with htmx, by a Ktor server run by Bootable.

`htmx` is the same todo list as [Compose HTML](https://rocketraman.github.io/bootable/bootable/next/examples/compose-html.html.md) and [Kilua](https://rocketraman.github.io/bootable/bootable/next/examples/kilua.html.md), rendered on the server with [kotlinx.html](https://github.com/kotlin/kotlinx.html), and made interactive with [htmx](https://htmx.org/).

`GET /` returns the whole page, already rendered, so the browser shows the todos before any script runs. The page’s elements carry htmx attributes, such as `hx-post`, `hx-patch`, and `hx-delete`, which htmx turns into requests; the server answers each with a fragment of HTML, which htmx swaps into the page. The page and the fragments are rendered by the same functions, in `Views.kt`, so the first render and later updates can’t disagree.

The server serves htmx’s script itself, at `/assets/htmx.min.js`, taken from htmx’s webjar by the build, so the application needs no CDN. Todos are kept in memory; [Server with a database](https://rocketraman.github.io/bootable/bootable/next/examples/server-ktor-db.html.md) stores data in a database.

## What it uses

- `boot-server-http-ktor-netty`, for `TodoServer`, a `NettyKtorService`; see [Server (Ktor)](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md).
- `boot-config-hoplite`, for the `server` config section; see [Configuration](https://rocketraman.github.io/bootable/bootable/next/configuration/index.html.md).
- `boot-logging-log4j2`, for the log; see [Logging (Log4j2)](https://rocketraman.github.io/bootable/bootable/next/logging/index.html.md).
- Ktor’s `ktor-server-htmx` and `ktor-htmx-html`, for the `hx { }` route scope and typed `hx-*` attributes.

The server is the only service, wired with plain Kotlin, as in [Compose HTML](https://rocketraman.github.io/bootable/bootable/next/examples/compose-html.html.md).

## Run it

From the repository root:

```shell
./gradlew :examples:htmx:run
```

Then open [http://localhost:8003](http://localhost:8003).

The routes the page calls, each answering with a fragment:

**`POST /todos`**

Adds a todo, returning its item, or, for a title that isn’t valid, a 422 with the form and the reason.

**`PATCH /todos/{id}`**

Marks a todo done or not, returning its item, or 404.

**`DELETE /todos/{id}`**

Removes a todo, or 404.

Parts of the page that a change affects elsewhere, such as the count of todos done, come in the same response, marked `hx-swap-oob`, and htmx swaps them in by id.

## Tests

```shell
./gradlew :examples:htmx:test
```

The tests make requests as htmx does, in Ktor’s test host, and check the full page, the fragments, the refusals, and that htmx’s script is served.

## Source

[`examples/htmx`](https://github.com/rocketraman/bootable/tree/master/examples/htmx); its README compares it with compose-html.
